package com.finance.service;

import com.finance.entity.Budget;
import java.util.List;

public interface BudgetService {

    List<Budget> listByMonth(Long userId, String month);

    void saveBudget(Long userId, Budget budget);

    void deleteBudget(Long id, Long userId);
}
