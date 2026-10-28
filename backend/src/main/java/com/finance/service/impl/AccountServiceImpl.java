package com.finance.service.impl;

import com.finance.common.exception.BusinessException;
import com.finance.entity.FinanceAccount;
import com.finance.entity.dto.AccountDTO;
import com.finance.mapper.FinanceAccountMapper;
import com.finance.service.AccountService;
import com.finance.service.DictionaryService;
import com.finance.service.AggregateCacheService;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import com.finance.service.DataRevisionService;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

@Service
public class AccountServiceImpl implements AccountService {
    @org.springframework.beans.factory.annotation.Autowired private DataRevisionService revision;

    @Autowired private FinanceAccountMapper accountMapper;
    @Autowired private DictionaryService dictionaryService;
    @Autowired private AggregateCacheService cache;

    @Override
    public List<FinanceAccount> list(Long userId) {
        LocalDate asOf = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        return cache.get(userId,"accounts|"+asOf,new TypeReference<List<FinanceAccount>>(){},()->{
            List<FinanceAccount> accounts=accountMapper.listWithBalances(userId,asOf);
            accounts.forEach(account->account.setAsOfDate(asOf));return accounts;
        });
    }

    @Override
    @Transactional
    public void create(Long userId, AccountDTO dto) {
        revision.bump(userId);
        if(dictionaryService.disabled("ACCOUNT_TYPE",dto.getAccountType()))throw new BusinessException(400,"该账户类型已停用");
        validateOpeningDate(dto.getOpeningDate());
        FinanceAccount account = new FinanceAccount();
        account.setUserId(userId);
        apply(account, dto);
        try { accountMapper.insert(account); }
        catch (DuplicateKeyException ex) { throw new BusinessException(409, "该账户名称已存在"); }
    }

    @Override
    @Transactional
    public void update(Long userId, Long id, AccountDTO dto) {
        revision.bump(userId);
        FinanceAccount account = lockAccessible(userId, id, null);
        validateOpeningDate(dto.getOpeningDate());
        boolean financialChange = !Objects.equals(account.getAccountType(), dto.getAccountType())
                || account.getOpeningBalance().compareTo(dto.getOpeningBalance()) != 0
                || !Objects.equals(account.getOpeningDate(), dto.getOpeningDate());
        if(!Objects.equals(account.getAccountType(),dto.getAccountType()) && dictionaryService.disabled("ACCOUNT_TYPE",dto.getAccountType()))throw new BusinessException(400,"该账户类型已停用");
        // Even deleted movements keep their original opening basis for historical traceability.
        if (financialChange && (accountMapper.countReferences(userId, id) > 0 || accountMapper.countPlanReferences(userId, id) > 0)) {
            throw new BusinessException(409, "账户已有流水或周期计划，不能修改类型、期初余额或期初日期");
        }
        apply(account, dto);
        try { accountMapper.updateById(account); }
        catch (DuplicateKeyException ex) { throw new BusinessException(409, "该账户名称已存在"); }
    }

    @Override
    @Transactional
    public void delete(Long userId, Long id) {
        revision.bump(userId);
        lockAccessible(userId, id, null);
        if (accountMapper.countActiveReferences(userId, id) > 0 || accountMapper.countPlanReferences(userId, id) > 0) {
            throw new BusinessException(409, "账户仍有有效流水或周期计划，不能删除");
        }
        accountMapper.deleteById(id);
    }

    @Override
    public FinanceAccount lockAccessible(Long userId, Long id, LocalDate billDate) {
        FinanceAccount account = accountMapper.selectActiveForUpdate(id);
        if (account == null) throw new BusinessException(404, "账户不存在");
        if (!userId.equals(account.getUserId())) throw new BusinessException(403, "无权访问该账户");
        if (billDate != null && billDate.isBefore(account.getOpeningDate())) {
            throw new BusinessException(400, "账单日期不能早于账户期初日期");
        }
        return account;
    }

    private void apply(FinanceAccount account, AccountDTO dto) {
        account.setName(dto.getName().trim());
        account.setAccountType(dto.getAccountType());
        account.setOpeningBalance(dto.getOpeningBalance());
        account.setOpeningDate(dto.getOpeningDate());
    }

    private void validateOpeningDate(LocalDate date) {
        if (date.isBefore(LocalDate.of(1000, 1, 1)) || date.isAfter(LocalDate.now(ZoneId.of("Asia/Shanghai")))) {
            throw new BusinessException(400, "期初日期不能晚于今天");
        }
    }
}
