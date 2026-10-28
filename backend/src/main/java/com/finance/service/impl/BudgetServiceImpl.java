package com.finance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.finance.common.exception.BusinessException;
import com.finance.common.i18n.I18nUtil;
import com.finance.entity.Budget;
import com.finance.entity.Category;
import com.finance.mapper.BillMapper;
import com.finance.mapper.BudgetMapper;
import com.finance.mapper.CategoryMapper;
import com.finance.service.BudgetService;
import com.finance.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.finance.service.DataRevisionService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

@Service
public class BudgetServiceImpl implements BudgetService {
    @org.springframework.beans.factory.annotation.Autowired private DataRevisionService revision;


    @Autowired
    private BudgetMapper budgetMapper;

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private BillMapper billMapper;

    @Autowired
    private I18nUtil i18nUtil;

    @Autowired
    private NotificationService notificationService;

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<Budget> listByMonth(Long userId, String month) {
        List<Budget> budgets = budgetMapper.selectList(
                new LambdaQueryWrapper<Budget>()
                        .eq(Budget::getUserId, userId)
                        .eq(Budget::getMonth, month)
        );

        List<Category> categories = categoryMapper.selectList(
                new LambdaQueryWrapper<Category>()
                        .and(wrapper -> wrapper.eq(Category::getUserId, userId)
                                .or()
                                .eq(Category::getUserId, 0L))
        );
        Map<Long, Category> categoryMap = categories.stream()
                .collect(Collectors.toMap(Category::getId, c -> c));

        String startDate = month + "-01";
        String endDate = YearMonth.parse(month).atEndOfMonth().toString();

        Map<Long, BigDecimal> byCategory = new HashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> row : billMapper.expenseByCategory(userId, startDate, endDate)) {
            BigDecimal amount = (BigDecimal) row.get("total_amount");
            total = total.add(amount);
            Number id = (Number) row.get("category_id");
            Category category = id == null ? null : categoryMap.get(id.longValue());
            if (category != null) {
                byCategory.merge(category.getId(), amount, BigDecimal::add);
                if (category.getParentId() != null) byCategory.merge(category.getParentId(), amount, BigDecimal::add);
            }
        }
        final BigDecimal totalSpent = total;

        budgets.forEach(budget -> {
            if (budget.getCategoryId() != null) {
                Category cat = categoryMap.get(budget.getCategoryId());
                if (cat != null) {
                    budget.setCategoryName(cat.getName());
                }
                budget.setSpentAmount(byCategory.getOrDefault(budget.getCategoryId(), BigDecimal.ZERO));
            } else {
                budget.setCategoryName(i18nUtil.getMessage("budget.total"));
                budget.setSpentAmount(totalSpent);
            }
        });

        return budgets;
    }

    @Override
    @Transactional
    public void saveBudget(Long userId, Budget budget) {
        revision.bump(userId);
        if (budget.getCategoryId() != null) {
            Category category = categoryMapper.selectActiveForUpdate(budget.getCategoryId());
            if (category == null) {
                throw new BusinessException(404, "分类不存在");
            }
            boolean accessible = userId.equals(category.getUserId()) || Long.valueOf(0L).equals(category.getUserId());
            if (!accessible) {
                throw new BusinessException(403, "无权为该分类设置预算");
            }
            if (!Integer.valueOf(1).equals(category.getType())) {
                throw new BusinessException(400, "只能为支出分类设置预算");
            }
        }
        budget.setUserId(userId);
        // 数据库有效记录唯一索引和原子 upsert 共同防止重复；总预算也受约束。
        budgetMapper.upsertActive(budget);
        notificationService.check(userId,YearMonth.parse(budget.getMonth()).atDay(1));
    }

    @Override
    @Transactional
    public void deleteBudget(Long id, Long userId) {
        revision.bump(userId);
        Budget budget = budgetMapper.selectById(id);
        if (budget == null) {
            throw new BusinessException(404, "预算不存在");
        }
        if (!userId.equals(budget.getUserId())) {
            throw new BusinessException(403, "无权删除该预算");
        }
        budgetMapper.deleteById(id);
    }
}
