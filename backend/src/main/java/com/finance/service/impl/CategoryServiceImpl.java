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
import com.finance.service.DataRevisionService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;

import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.HashMap;

@Service
public class CategoryServiceImpl implements CategoryService {
    @org.springframework.beans.factory.annotation.Autowired private DataRevisionService revision;


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
                         .orderByAsc(Category::getId)
        );
    }

    @Override
    @Transactional
    public void addCategory(Long userId, Category category) {
        revision.bump(userId);
        if (category.getParentId() != null) {
            Category parent = categoryMapper.selectActiveForUpdate(category.getParentId());
            validateParent(userId, category, parent);
        }
        category.setUserId(userId);
        try {
            categoryMapper.insert(category);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(409, i18nUtil.getMessage("category.duplicate"));
        }
    }

    @Override
    @Transactional
    public void updateCategory(Long userId, Category category) {
        updateCategory(userId, category, false);
    }

    @Override
    @Transactional
    public void updateCategory(Long userId, Category category, boolean parentSpecified) {
        revision.bump(userId);
        TreeSet<Long> ids = new TreeSet<>();
        ids.add(category.getId());
        if (parentSpecified && category.getParentId() != null) ids.add(category.getParentId());
        Map<Long, Category> locked = new HashMap<>();
        for (Long id : ids) locked.put(id, categoryMapper.selectActiveForUpdate(id));
        Category existing = locked.get(category.getId());
        if (existing == null) {
            throw new BusinessException(404, "分类不存在");
        }
        if (Long.valueOf(0L).equals(existing.getUserId()) && !Long.valueOf(0L).equals(userId)) {
            throw new BusinessException(403, "系统默认分类不可修改");
        }
        if (!userId.equals(existing.getUserId())) {
            throw new BusinessException(403, "无权修改该分类");
        }
        if (!Objects.equals(existing.getType(), category.getType())) {
            throw new BusinessException(400, i18nUtil.getMessage("category.type.immutable"));
        }
        if (parentSpecified) {
            if (Objects.equals(category.getId(), category.getParentId())) {
                throw new BusinessException(400, "分类不能作为自己的父分类");
            }
            if (category.getParentId() != null) {
                validateParent(userId, category, locked.get(category.getParentId()));
                Long children = categoryMapper.selectCount(new LambdaQueryWrapper<Category>()
                        .eq(Category::getParentId, existing.getId()));
                if (children != null && children > 0) throw new BusinessException(409, "已有子分类，不能调整为二级分类");
            }
            existing.setParentId(category.getParentId());
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
        revision.bump(userId);
        Category category = categoryMapper.selectActiveForUpdate(id);
        if (category == null) {
            throw new BusinessException(404, "分类不存在");
        }
        if (Long.valueOf(0L).equals(category.getUserId()) && !Long.valueOf(0L).equals(userId)) {
            throw new BusinessException(403, "系统默认分类不可删除");
        }
        if (!userId.equals(category.getUserId())) {
            throw new BusinessException(403, "无权删除该分类");
        }
        Long children = categoryMapper.selectCount(new LambdaQueryWrapper<Category>().eq(Category::getParentId, id));
        if (children != null && children > 0) throw new BusinessException(409, "分类仍有子分类，不能删除");
        if (categoryMapper.countPlanReferences(id) > 0) throw new BusinessException(409, "分类已被周期计划使用，不能删除");
        Long referencedBillCount = billMapper.selectCount(
                new LambdaQueryWrapper<Bill>()
                        .eq(!Long.valueOf(0L).equals(userId),Bill::getUserId, userId)
                        .eq(Bill::getCategoryId, id)
        );
        if (referencedBillCount != null && referencedBillCount > 0) {
            throw new BusinessException(409, i18nUtil.getMessage("category.delete.in.use"));
        }
        Long referencedBudgetCount = budgetMapper.selectCount(new LambdaQueryWrapper<Budget>()
                .eq(!Long.valueOf(0L).equals(userId),Budget::getUserId, userId).eq(Budget::getCategoryId, id));
        if (referencedBudgetCount != null && referencedBudgetCount > 0) {
            throw new BusinessException(409, i18nUtil.getMessage("category.delete.budget.in.use"));
        }
        categoryMapper.deleteById(id);
    }

    @Override
    @Transactional
    public void reorder(Long userId, Long id, Long targetId) {
        if (Objects.equals(id, targetId)) throw new BusinessException(400, "请选择相邻的两个分类");
        revision.bump(userId);
        Map<Long, Category> locked = new HashMap<>();
        for (Long key : new TreeSet<>(java.util.Arrays.asList(id, targetId))) {
            Category category = categoryMapper.selectActiveForUpdate(key);
            if (category == null) throw new BusinessException(404, "分类不存在");
            if (!userId.equals(category.getUserId()) || Long.valueOf(0).equals(category.getUserId())) {
                throw new BusinessException(403, "只能调整自己的分类排序");
            }
            locked.put(key, category);
        }
        Category current = locked.get(id), target = locked.get(targetId);
        if (!Objects.equals(current.getType(), target.getType())
                || !Objects.equals(current.getParentId(), target.getParentId())) {
            throw new BusinessException(400, "只能调整同级同类型分类");
        }
        List<Category> siblings = categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .eq(Category::getUserId, userId).eq(Category::getType, current.getType())
                .eq(current.getParentId() != null, Category::getParentId, current.getParentId())
                .isNull(current.getParentId() == null, Category::getParentId)
                .orderByAsc(Category::getSortOrder).orderByAsc(Category::getId));
        int from = -1, to = -1;
        for (int i = 0; i < siblings.size(); i++) {
            if (id.equals(siblings.get(i).getId())) from = i;
            if (targetId.equals(siblings.get(i).getId())) to = i;
        }
        if (from < 0 || to < 0 || Math.abs(from - to) != 1) {
            throw new BusinessException(409, "分类顺序已变化，请刷新后重试");
        }
        java.util.Collections.swap(siblings, from, to);
        // 归一化当前同级顺序，保证原有相同sortOrder也能移动；仅更新排序字段。
        for (int i = 0; i < siblings.size(); i++) {
            Category sibling = siblings.get(i);
            if (!Integer.valueOf(i + 1).equals(sibling.getSortOrder())
                    && categoryMapper.updateOrder(sibling.getId(), userId, i + 1) != 1) {
                throw new BusinessException(409, "分类状态已变化，请刷新后重试");
            }
        }
    }

    private void validateParent(Long userId, Category child, Category parent) {
        if (parent == null) throw new BusinessException(404, "父分类不存在");
        if (!userId.equals(parent.getUserId()) && !Long.valueOf(0L).equals(parent.getUserId())) {
            throw new BusinessException(403, "无权使用该父分类");
        }
        if (parent.getParentId() != null) throw new BusinessException(400, "分类最多支持两级");
        if (!Objects.equals(parent.getType(), child.getType())) throw new BusinessException(400, "父子分类类型必须一致");
    }
}
