package com.finance.service;

import com.finance.entity.FinanceAccount;
import com.finance.entity.dto.AccountDTO;
import java.time.LocalDate;
import java.util.List;

public interface AccountService {
    List<FinanceAccount> list(Long userId);
    void create(Long userId, AccountDTO dto);
    void update(Long userId, Long id, AccountDTO dto);
    void delete(Long userId, Long id);
    FinanceAccount lockAccessible(Long userId, Long id, LocalDate billDate);
}
