package com.finance.service;

import com.finance.entity.FinanceAccount;
import com.finance.mapper.BillMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import com.fasterxml.jackson.core.type.TypeReference;

@Service
public class AssetService {
    private final AccountService accounts;
    private final BillMapper bills;
    private final AggregateCacheService cache;
    public AssetService(AccountService accounts, BillMapper bills,AggregateCacheService cache) { this.accounts=accounts; this.bills=bills;this.cache=cache; }

    @Transactional(readOnly = true)
    public Map<String,Object> snapshot(Long userId) {
        return cache.get(userId,"assets|"+RecurringService.today(),new TypeReference<Map<String,Object>>(){},()->fromDatabase(userId));
    }

    private Map<String,Object> fromDatabase(Long userId) {
        List<FinanceAccount> list = accounts.list(userId);
        LocalDate date = list.isEmpty() ? RecurringService.today() : list.get(0).getAsOfDate();
        BigDecimal assets=BigDecimal.ZERO, liabilities=BigDecimal.ZERO;
        for (FinanceAccount account : list) {
            BigDecimal value=account.getBalance();
            if (value.signum() >= 0) assets=assets.add(value);
            else liabilities=liabilities.add(value.negate());
        }
        LocalDate start=date.minusDays(29);
        BigDecimal income=bills.sumByTypeAndDateRange(userId,2,start.toString(),date.toString());
        BigDecimal expense=bills.sumByTypeAndDateRange(userId,1,start.toString(),date.toString());
        Map<String,Object> result=new HashMap<>();
        result.put("asOfDate",date); result.put("assets",assets); result.put("liabilities",liabilities);
        result.put("netAssets",assets.subtract(liabilities)); result.put("accounts",list);
        result.put("cashflowIncome",income); result.put("cashflowExpense",expense); result.put("cashflowNet",income.subtract(expense));
        result.put("unassignedBillCount",bills.countUnassigned(userId,date.toString()));
        result.put("dailyFlow", dailyRows(bills.sumByDayRange(userId,start.toString(),date.toString()),start,date));
        return result;
    }

    public static List<Map<String,Object>> dailyRows(List<Map<String,Object>> raw, LocalDate start, LocalDate end) {
        Map<String,Map<String,Object>> values=new HashMap<>();
        for (Map<String,Object> row:raw) values.put(row.get("day").toString(),row);
        List<Map<String,Object>> result=new ArrayList<>();
        for (LocalDate date=start; !date.isAfter(end); date=date.plusDays(1)) {
            Map<String,Object> row=new HashMap<>();
            Map<String,Object> value=values.get(date.toString());
            row.put("day",date.toString()); row.put("income",value==null?BigDecimal.ZERO:value.get("income"));
            row.put("expense",value==null?BigDecimal.ZERO:value.get("expense")); result.add(row);
        }
        return result;
    }
}
