package com.finance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.finance.common.exception.BusinessException;
import com.finance.entity.Bill;
import com.finance.entity.Category;
import com.finance.entity.FinanceAccount;
import com.finance.mapper.BillMapper;
import com.finance.mapper.CategoryMapper;
import com.finance.mapper.FinanceAccountMapper;
import com.finance.service.AccountService;
import com.finance.service.BillService;
import com.finance.service.NotificationService;
import com.finance.service.AggregateCacheService;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.finance.service.DataRevisionService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import com.finance.service.AssetService;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.Locale;
import java.util.TreeSet;
import java.util.Arrays;
import java.util.stream.Collectors;

@Service
public class BillServiceImpl implements BillService {
    @org.springframework.beans.factory.annotation.Autowired private DataRevisionService revision;


    @Autowired
    private BillMapper billMapper;

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private AccountService accountService;

    @Autowired
    private FinanceAccountMapper accountMapper;

    @Autowired
    private NotificationService notificationService;

    @Autowired private AggregateCacheService cache;

    @Override
    public List<Map<String,Object>> getDayStats(Long userId, Integer type, String start, String end) {
        LocalDate from=LocalDate.parse(start), to=LocalDate.parse(end);
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from,to)>365) throw new BusinessException(400,"日统计请选择一年以内的有效日期范围");
        if (type == null || (type != 1 && type != 2)) throw new BusinessException(400,"统计类型只能为收入或支出");
        return cache.get(userId,"day|"+type+"|"+start+"|"+end,new TypeReference<List<Map<String,Object>>>(){},()->{
            List<Map<String,Object>> rows=AssetService.dailyRows(billMapper.sumByDayRange(userId,start,end),from,to);
            rows.forEach(row -> row.put("total_amount",row.get(type==1?"expense":"income")));return rows;
        });
    }

    @Override
    @Transactional
    public void addBill(Long userId, Bill bill) {
        revision.bump(userId);
        ensureCategoryAccessible(userId, bill.getCategoryId(), bill.getType());
        if (bill.getAccountId() != null) accountService.lockAccessible(userId, bill.getAccountId(), bill.getBillDate());
        bill.setUserId(userId);
        billMapper.insert(bill);
        if(Integer.valueOf(1).equals(bill.getType()))notificationService.check(userId,bill.getBillDate());
    }

    @Override
    @Transactional
    public Bill transfer(Long userId, Bill bill) {
        revision.bump(userId);
        if (Objects.equals(bill.getAccountId(), bill.getTransferAccountId())) {
            throw new BusinessException(400, "转出和转入账户不能相同");
        }
        // 两端按相同顺序加锁；重复请求在首笔提交后读到同一记录，不重复入账。
        for (Long id : new TreeSet<>(Arrays.asList(bill.getAccountId(), bill.getTransferAccountId()))) {
            accountService.lockAccessible(userId, id, bill.getBillDate());
        }
        bill.setRequestId(bill.getRequestId().toLowerCase(Locale.ROOT));
        Bill existing = billMapper.selectTransferRequest(userId, bill.getRequestId());
        if (existing != null) {
            if (Integer.valueOf(1).equals(existing.getDeleted())) {
                throw new BusinessException(409, "该转账已撤销，请使用新的请求编号");
            }
            if (!Objects.equals(existing.getAccountId(), bill.getAccountId())
                    || !Objects.equals(existing.getTransferAccountId(), bill.getTransferAccountId())
                    || existing.getAmount().compareTo(bill.getAmount()) != 0
                    || !Objects.equals(existing.getBillDate(), bill.getBillDate())
                    || !Objects.equals(existing.getRemark(), bill.getRemark())) {
                throw new BusinessException(409, "请求编号已用于另一笔转账");
            }
            return existing;
        }
        bill.setType(3);
        bill.setCategoryId(null);
        bill.setUserId(userId);
        try { billMapper.insert(bill); }
        catch (DuplicateKeyException ex) { throw new BusinessException(409, "请求编号已用于另一笔转账"); }
        return bill;
    }

    @Override
    @Transactional
    public void updateBill(Long userId, Bill bill) {
        revision.bump(userId);
        Bill existing = billMapper.selectById(bill.getId());
        if (existing == null) {
            throw new BusinessException(404, "账单不存在");
        }
        if (!userId.equals(existing.getUserId())) {
            throw new BusinessException(403, "无权修改该账单");
        }
        if (Integer.valueOf(3).equals(existing.getType())) {
            throw new BusinessException(400, "转账不能改为普通收支；如需更正，请撤销后重新记账");
        }
        ensureCategoryAccessible(userId, bill.getCategoryId(), bill.getType());
        // 旧客户端未提交账户字段时保留原归属，而不是意外移出账户。
        Long accountId = bill.getAccountId() != null ? bill.getAccountId() : existing.getAccountId();
        if (accountId != null) accountService.lockAccessible(userId, accountId, bill.getBillDate());
        existing.setAccountId(accountId);
        existing.setCategoryId(bill.getCategoryId());
        existing.setAmount(bill.getAmount());
        existing.setType(bill.getType());
        existing.setRemark(bill.getRemark());
        existing.setBillDate(bill.getBillDate());
        billMapper.updateById(existing);
        if(Integer.valueOf(1).equals(bill.getType()))notificationService.check(userId,bill.getBillDate());
    }

    @Override
    @Transactional
    public void deleteBill(Long id, Long userId) {
        revision.bump(userId);
        Bill bill = billMapper.selectById(id);
        if (bill == null) {
            throw new BusinessException(404, "账单不存在");
        }
        if (!userId.equals(bill.getUserId())) {
            throw new BusinessException(403, "无权删除该账单");
        }
        billMapper.deleteById(id);
    }

    @Override
    @Transactional
    public void deleteBatch(Long userId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        revision.bump(userId);
        Set<Long> requestedIds = new HashSet<>(ids);
        List<Bill> ownedBills = billMapper.selectList(new LambdaQueryWrapper<Bill>()
                .eq(Bill::getUserId, userId)
                .in(Bill::getId, requestedIds));
        if (ownedBills.size() != requestedIds.size()) {
            throw new BusinessException(403, "批量删除包含无权访问的账单");
        }
        billMapper.delete(new LambdaQueryWrapper<Bill>()
                .eq(Bill::getUserId, userId)
                .in(Bill::getId, requestedIds));
    }

    @Override
    public IPage<Bill> pageList(Long userId, Integer type, Long categoryId, String startDate, String endDate,
                                BigDecimal minAmount, BigDecimal maxAmount, int page, int size, Long accountId) {
        LambdaQueryWrapper<Bill> wrapper = new LambdaQueryWrapper<Bill>()
                .eq(Bill::getUserId, userId)
                .eq(type != null, Bill::getType, type)
                .eq(categoryId != null, Bill::getCategoryId, categoryId)
                .and(accountId != null, clause -> clause.eq(Bill::getAccountId, accountId)
                        .or().eq(Bill::getTransferAccountId, accountId))
                .ge(startDate != null, Bill::getBillDate, startDate)
                .le(endDate != null, Bill::getBillDate, endDate)
                .ge(minAmount != null, Bill::getAmount, minAmount)
                .le(maxAmount != null, Bill::getAmount, maxAmount)
                .orderByDesc(Bill::getBillDate)
                .orderByDesc(Bill::getCreateTime)
                .orderByDesc(Bill::getId);

        IPage<Bill> pageResult = billMapper.selectPage(new Page<>(page, size), wrapper);

        fillCategoryDetails(userId, pageResult.getRecords());

        return pageResult;
    }

    @Override
    public List<Bill> getRecentBills(Long userId, int limit) {
        // 使用 MyBatis-Plus 标准分页参数，杜绝字符串拼接 SQL
        Page<Bill> page = new Page<>(1, limit);
        LambdaQueryWrapper<Bill> wrapper = new LambdaQueryWrapper<Bill>()
                .eq(Bill::getUserId, userId)
                .orderByDesc(Bill::getBillDate)
                .orderByDesc(Bill::getCreateTime)
                .orderByDesc(Bill::getId);

        Page<Bill> pageResult = billMapper.selectPage(page, wrapper);
        List<Bill> bills = pageResult.getRecords();

        fillCategoryDetails(userId, bills);

        return bills;
    }

    @Override
    public Map<String, Object> getStatistics(Long userId, String startDate, String endDate) {
        return cache.get(userId,"summary|"+startDate+"|"+endDate,new TypeReference<Map<String,Object>>(){},()->statisticsFromDatabase(userId,startDate,endDate));
    }

    private Map<String,Object> statisticsFromDatabase(Long userId,String startDate,String endDate) {
        // 同一条SQL快照内计算两侧，避免重分类并发写入造成双计或漏计。
        Map<String, BigDecimal> totals = billMapper.sumSummary(userId, startDate, endDate);
        BigDecimal income = totals.get("income");
        BigDecimal expense = totals.get("expense");
        BigDecimal balance = income.subtract(expense);

        Map<String, Object> result = new HashMap<>();
        result.put("income", income);
        result.put("expense", expense);
        result.put("balance", balance);
        return result;
    }

    @Override
    public List<Map<String, Object>> getCategoryStats(Long userId, Integer type, String startDate, String endDate) {
        return cache.get(userId,"category|"+type+"|"+startDate+"|"+endDate,new TypeReference<List<Map<String,Object>>>(){},()->billMapper.sumByCategory(userId,type,startDate,endDate));
    }

    @Override
    public List<Map<String, Object>> getMonthStats(Long userId, Integer type, String startDate, String endDate) {
        return cache.get(userId,"month|"+type+"|"+startDate+"|"+endDate,new TypeReference<List<Map<String,Object>>>(){},()->billMapper.sumByMonth(userId,type,startDate,endDate));
    }

    @Override
    public List<Map<String, Object>> getYearStats(Long userId, int year) {
        String start = year + "-01-01";
        String end = year + "-12-31";
        return cache.get(userId,"year|"+year,new TypeReference<List<Map<String,Object>>>(){},()->billMapper.sumByMonthRange(userId,start,end));
    }

    @Override
    public List<Map<String, Object>> getMonthRangeSummary(Long userId, String startDate, String endDate) {
        return cache.get(userId,"months-summary|"+startDate+"|"+endDate,new TypeReference<List<Map<String,Object>>>(){},()->billMapper.sumByMonthRange(userId,startDate,endDate));
    }

    private void fillCategoryDetails(Long userId, List<Bill> bills) {
        List<Category> categories = categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .and(wrapper -> wrapper.eq(Category::getUserId, userId)
                        .or()
                        .eq(Category::getUserId, 0L)));
        Map<Long, Category> categoryMap = categories.stream()
                .collect(Collectors.toMap(Category::getId, c -> c));
        bills.forEach(bill -> {
            Category cat = categoryMap.get(bill.getCategoryId());
            if (cat != null) {
                bill.setCategoryName(cat.getName());
                bill.setCategoryIcon(cat.getIcon());
            }
        });
        if (bills.stream().anyMatch(bill -> bill.getAccountId() != null)) {
            Map<Long, FinanceAccount> accounts = accountMapper.selectList(new LambdaQueryWrapper<FinanceAccount>()
                    .eq(FinanceAccount::getUserId, userId)).stream()
                    .collect(Collectors.toMap(FinanceAccount::getId, account -> account));
            bills.forEach(bill -> {
                FinanceAccount source = accounts.get(bill.getAccountId());
                FinanceAccount target = accounts.get(bill.getTransferAccountId());
                if (source != null) bill.setAccountName(source.getName());
                if (target != null) bill.setTransferAccountName(target.getName());
            });
        }
    }

    private void ensureCategoryAccessible(Long userId, Long categoryId, Integer billType) {
        Category category = categoryMapper.selectActiveForUpdate(categoryId);
        if (category == null) {
            throw new BusinessException(404, "分类不存在");
        }
        boolean accessible = userId.equals(category.getUserId()) || Long.valueOf(0L).equals(category.getUserId());
        if (!accessible) {
            throw new BusinessException(403, "无权使用该分类");
        }
        if (billType != null && !billType.equals(category.getType())) {
            throw new BusinessException(400, "账单类型与分类类型不一致");
        }
    }
}
