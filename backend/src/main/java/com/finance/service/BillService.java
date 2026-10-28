package com.finance.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.finance.entity.Bill;

import java.math.BigDecimal;
import java.util.Map;
import java.util.List;

public interface BillService {

    void addBill(Long userId, Bill bill);

    Bill transfer(Long userId, Bill bill);

    List<Map<String,Object>> getDayStats(Long userId, Integer type, String start, String end);

    void updateBill(Long userId, Bill bill);

    void deleteBill(Long id, Long userId);

    void deleteBatch(Long userId, List<Long> ids);

    IPage<Bill> pageList(Long userId, Integer type, Long categoryId, String startDate, String endDate,
                         BigDecimal minAmount, BigDecimal maxAmount, int page, int size, Long accountId);

    Map<String, Object> getStatistics(Long userId, String startDate, String endDate);

    List<Map<String, Object>> getCategoryStats(Long userId, Integer type, String startDate, String endDate);

    List<Map<String, Object>> getMonthStats(Long userId, Integer type, String startDate, String endDate);

    List<Bill> getRecentBills(Long userId, int limit);

    /** 年度统计：全年12月收支明细 */
    List<Map<String, Object>> getYearStats(Long userId, int year);

    /** 月度汇总报表 */
    List<Map<String, Object>> getMonthRangeSummary(Long userId, String startDate, String endDate);
}
