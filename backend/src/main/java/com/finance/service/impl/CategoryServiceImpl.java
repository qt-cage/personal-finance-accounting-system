package com.finance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.finance.common.exception.BusinessException;
import com.finance.common.i18n.I18nUtil;
import com.finance.entity.Bill;
import com.finance.entity.Budget;
import com.finance.mapper.BudgetMapper;
import com.finance.entity.Category;
import com.finance.mapper.BillMapper;
import com.finance.mapper.CategoryMapper;
import com.finance.service.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;

import java.util.List;

@Service
public class CategoryServiceImpl implements CategoryService {

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private BillMapper billMapper;

    @Autowired
    private BudgetMapper budgetMapper;

    @Autowired
    private I18nUtil i18nUtil;

    @Override
    public List<Category> listByUserId(Long userId, Integer type) {
        return categoryMapper.selectList(
                new LambdaQueryWrapper<Category>()
                        .and(wrapper -> wrapper.eq(Category::getUserId, userId).or().eq(Category::getUserId, 0))
                        .eq(type != null, Category::getType, type)
                        .orderByAsc(Category::getSortOrder)
        );
    }

    @Override
    public void addCategory(Long userId, Category category) {
        category.setUserId(userId);
        try {
            categoryMapper.insert(category);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(409, i18nUtil.getMessage("category.duplicate"));
        }
    }

    @Override
    public void updateCategory(Long userId, Category category) {
        Category existing = categoryMapper.selectById(category.getId());
        if (existing == null) {
            throw new BusinessException(404, "分类不存在");
        }
        if (Long.valueOf(0L).equals(existing.getUserId())) {
            throw new BusinessException(403, "系统默认分类不可修改");
        }
        if (!userId.equals(existing.getUserId())) {
            throw new BusinessException(403, "无权修改该分类");
        }
        if (!Objects.equals(existing.getType(), category.getType())) {
            throw new BusinessException(400, i18nUtil.getMessage("category.type.immutable"));
        }
        existing.setName(category.getName());
        existing.setType(category.getType());
        existing.setIcon(category.getIcon());
        existing.setSortOrder(category.getSortOrder());
        try {
            categoryMapper.updateById(existing);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(409, i18nUtil.getMessage("category.duplicate"));
        }
    }

    @Override
    @Transactional
    public void deleteCategory(Long id, Long userId) {
        Category category = categoryMapper.selectActiveForUpdate(id);
        if (category == null) {
            throw new BusinessException(404, "分类不存在");
        }
        if (Long.valueOf(0L).equals(category.getUserId())) {
            throw new BusinessException(403, "系统默认分类不可删除");
        }
        if (!userId.equals(category.getUserId())) {
            throw new BusinessException(403, "无权删除该分类");
        }
        Long referencedBillCount = billMapper.selectCount(
                new LambdaQueryWrapper<Bill>()
                        .eq(Bill::getUserId, userId)
                        .eq(Bill::getCategoryId, id)
        );
        if (referencedBillCount != null && referencedBillCount > 0) {
            throw new BusinessException(409, i18nUtil.getMessage("category.delete.in.use"));
        }
        Long referencedBudgetCount = budgetMapper.selectCount(new LambdaQueryWrapper<Budget>()
                .eq(Budget::getUserId, userId).eq(Budget::getCategoryId, id));
        if (referencedBudgetCount != null && referencedBudgetCount > 0) {
            throw new BusinessException(409, i18nUtil.getMessage("category.delete.budget.in.use"));
        }
        categoryMapper.deleteById(id);
    }
}
