package com.finance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.finance.common.exception.BusinessException;
import com.finance.entity.Bill;
import com.finance.entity.Category;
import com.finance.mapper.BillMapper;
import com.finance.mapper.CategoryMapper;
import com.finance.service.BillService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BillServiceImpl implements BillService {

    @Autowired
    private BillMapper billMapper;

    @Autowired
    private CategoryMapper categoryMapper;

    @Override
    @Transactional
    public void addBill(Long userId, Bill bill) {
        ensureCategoryAccessible(userId, bill.getCategoryId(), bill.getType());
        bill.setUserId(userId);
        billMapper.insert(bill);
    }

    @Override
    @Transactional
    public void updateBill(Long userId, Bill bill) {
        Bill existing = billMapper.selectById(bill.getId());
        if (existing == null) {
            throw new BusinessException(404, "账单不存在");
        }
        if (!userId.equals(existing.getUserId())) {
            throw new BusinessException(403, "无权修改该账单");
        }
        ensureCategoryAccessible(userId, bill.getCategoryId(), bill.getType());
        existing.setCategoryId(bill.getCategoryId());
        existing.setAmount(bill.getAmount());
        existing.setType(bill.getType());
        existing.setRemark(bill.getRemark());
        existing.setBillDate(bill.getBillDate());
        billMapper.updateById(existing);
    }

    @Override
    public void deleteBill(Long id, Long userId) {
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
    public void deleteBatch(Long userId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
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
                                BigDecimal minAmount, BigDecimal maxAmount, int page, int size) {
        LambdaQueryWrapper<Bill> wrapper = new LambdaQueryWrapper<Bill>()
                .eq(Bill::getUserId, userId)
                .eq(type != null, Bill::getType, type)
                .eq(categoryId != null, Bill::getCategoryId, categoryId)
                .ge(startDate != null, Bill::getBillDate, startDate)
                .le(endDate != null, Bill::getBillDate, endDate)
                .ge(minAmount != null, Bill::getAmount, minAmount)
                .le(maxAmount != null, Bill::getAmount, maxAmount)
                .orderByDesc(Bill::getBillDate)
                .orderByDesc(Bill::getCreateTime);

        IPage<Bill> pageResult = billMapper.selectPage(new Page<>(page, size), wrapper);

        List<Category> categories = listAccessibleCategories(userId);
        Map<Long, Category> categoryMap = categories.stream()
                .collect(Collectors.toMap(Category::getId, c -> c));

        pageResult.getRecords().forEach(bill -> {
            Category cat = categoryMap.get(bill.getCategoryId());
            if (cat != null) {
                bill.setCategoryName(cat.getName());
                bill.setCategoryIcon(cat.getIcon());
            }
        });

        return pageResult;
    }

    @Override
    public List<Bill> getRecentBills(Long userId, int limit) {
        // 使用 MyBatis-Plus 标准分页参数，杜绝字符串拼接 SQL
        Page<Bill> page = new Page<>(1, limit);
        LambdaQueryWrapper<Bill> wrapper = new LambdaQueryWrapper<Bill>()
                .eq(Bill::getUserId, userId)
                .orderByDesc(Bill::getBillDate)
                .orderByDesc(Bill::getCreateTime);

        Page<Bill> pageResult = billMapper.selectPage(page, wrapper);
        List<Bill> bills = pageResult.getRecords();

        List<Category> categories = listAccessibleCategories(userId);
        Map<Long, Category> categoryMap = categories.stream()
                .collect(Collectors.toMap(Category::getId, c -> c));

        bills.forEach(bill -> {
            Category cat = categoryMap.get(bill.getCategoryId());
            if (cat != null) {
                bill.setCategoryName(cat.getName());
                bill.setCategoryIcon(cat.getIcon());
            }
        });

        return bills;
    }

    // 以下方法保持不变
    @Override
    public Map<String, Object> getStatistics(Long userId, String startDate, String endDate) {
        BigDecimal income = billMapper.sumByTypeAndDateRange(userId, 2, startDate, endDate);
        BigDecimal expense = billMapper.sumByTypeAndDateRange(userId, 1, startDate, endDate);
        BigDecimal balance = income.subtract(expense);

        Map<String, Object> result = new HashMap<>();
        result.put("income", income);
        result.put("expense", expense);
        result.put("balance", balance);
        return result;
    }

    @Override
    public List<Map<String, Object>> getCategoryStats(Long userId, Integer type, String startDate, String endDate) {
        return billMapper.sumByCategory(userId, type, startDate, endDate);
    }

    @Override
    public List<Map<String, Object>> getMonthStats(Long userId, Integer type, String startDate, String endDate) {
        return billMapper.sumByMonth(userId, type, startDate, endDate);
    }

    @Override
    public List<Map<String, Object>> getYearStats(Long userId, int year) {
        String start = year + "-01-01";
        String end = year + "-12-31";
        return billMapper.sumByYear(userId, start, end);
    }

    @Override
    public List<Map<String, Object>> getMonthRangeSummary(Long userId, String startDate, String endDate) {
        return billMapper.sumByMonthRange(userId, startDate, endDate);
    }

    private List<Category> listAccessibleCategories(Long userId) {
        return categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .and(wrapper -> wrapper.eq(Category::getUserId, userId)
                        .or()
                        .eq(Category::getUserId, 0L)));
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
