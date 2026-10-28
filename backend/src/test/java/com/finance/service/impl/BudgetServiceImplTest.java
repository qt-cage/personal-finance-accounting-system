package com.finance.service.impl;
import com.finance.service.DataRevisionService;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.finance.common.exception.BusinessException;
import com.finance.common.i18n.I18nUtil;
import com.finance.entity.Budget;
import com.finance.entity.Category;
import com.finance.mapper.BillMapper;
import com.finance.mapper.BudgetMapper;
import com.finance.mapper.CategoryMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import com.finance.service.NotificationService;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BudgetServiceImplTest {

    @Mock
    private BudgetMapper budgetMapper;
    @Mock
    private CategoryMapper categoryMapper;
    @Mock
    private BillMapper billMapper;
    @Mock
    private I18nUtil i18nUtil;

    @Mock
    private NotificationService notificationService;

    @Mock private DataRevisionService revision;
    @InjectMocks
    private BudgetServiceImpl service;

    @Test
    void listUsesCategorySpendForCategoryBudgetAndTotalSpendForTotalBudget() {
        Budget categoryBudget = budget(1L, 1L, 10L);
        Budget totalBudget = budget(2L, 1L, null);
        Category category = category(10L, 1L, 1);
        category.setName("餐饮");
        when(budgetMapper.selectList(any(Wrapper.class))).thenReturn(Arrays.asList(categoryBudget, totalBudget));
        when(categoryMapper.selectList(any(Wrapper.class))).thenReturn(Collections.singletonList(category));
        java.util.Map<String,Object> dining=new java.util.HashMap<>(),other=new java.util.HashMap<>();
        dining.put("category_id",10L);dining.put("total_amount",new BigDecimal("120.00"));
        other.put("category_id",11L);other.put("total_amount",new BigDecimal("380.00"));
        when(billMapper.expenseByCategory(1L, "2026-02-01", "2026-02-28")).thenReturn(Arrays.asList(dining,other));
        when(i18nUtil.getMessage("budget.total")).thenReturn("总预算");

        List<Budget> result = service.listByMonth(1L, "2026-02");

        assertEquals(new BigDecimal("120.00"), result.get(0).getSpentAmount());
        assertEquals(new BigDecimal("500.00"), result.get(1).getSpentAmount());
        assertEquals("总预算", result.get(1).getCategoryName());
    }

    @Test
    void saveRejectsIncomeCategory() {
        Budget budget = budget(null, null, 10L);
        when(categoryMapper.selectActiveForUpdate(10L)).thenReturn(category(10L, 1L, 2));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.saveBudget(1L, budget));

        assertEquals(400, ex.getCode());
    }

    @Test
    void deleteRejectsCrossUserBudget() {
        when(budgetMapper.selectById(2L)).thenReturn(budget(2L, 9L, null));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.deleteBudget(2L, 1L));

        assertEquals(403, ex.getCode());
    }

    private static Budget budget(Long id, Long userId, Long categoryId) {
        Budget budget = new Budget();
        budget.setId(id);
        budget.setUserId(userId);
        budget.setCategoryId(categoryId);
        budget.setAmount(new BigDecimal("1000.00"));
        budget.setMonth("2026-02");
        return budget;
    }

    private static Category category(Long id, Long userId, int type) {
        Category category = new Category();
        category.setId(id);
        category.setUserId(userId);
        category.setType(type);
        return category;
    }
}
