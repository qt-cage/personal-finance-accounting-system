package com.finance.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.finance.common.i18n.I18nUtil;
import com.finance.common.result.Result;
import com.finance.entity.Bill;
import com.finance.entity.dto.BillDTO;
import com.finance.entity.dto.BillUpdateDTO;
import com.finance.service.BillService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bill")
public class BillController {

    @Autowired
    private BillService billService;

    @Autowired
    private I18nUtil i18nUtil;

    @PostMapping
    public Result<Void> add(HttpServletRequest request, @Validated @RequestBody BillDTO dto) {
        Long userId = (Long) request.getAttribute("userId");
        Bill bill = new Bill();
        bill.setCategoryId(dto.getCategoryId());
        bill.setAmount(dto.getAmount());
        bill.setType(dto.getType());
        bill.setRemark(dto.getRemark());
        bill.setBillDate(LocalDate.parse(dto.getBillDate()));
        billService.addBill(userId, bill);
        return Result.success(i18nUtil.getMessage("bill.add.success"));
    }

    @PutMapping
    public Result<Void> update(HttpServletRequest request, @Validated @RequestBody BillUpdateDTO dto) {
        Long userId = (Long) request.getAttribute("userId");
        Bill bill = new Bill();
        bill.setId(dto.getId());
        bill.setCategoryId(dto.getCategoryId());
        bill.setAmount(dto.getAmount());
        bill.setType(dto.getType());
        bill.setRemark(dto.getRemark());
        bill.setBillDate(LocalDate.parse(dto.getBillDate()));
        billService.updateBill(userId, bill);
        return Result.success(i18nUtil.getMessage("bill.update.success"));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(HttpServletRequest request, @PathVariable Long id) {
        Long userId = (Long) request.getAttribute("userId");
        billService.deleteBill(id, userId);
        return Result.success(i18nUtil.getMessage("bill.delete.success"));
    }

    @DeleteMapping("/batch")
    public Result<Void> deleteBatch(HttpServletRequest request, @RequestBody List<Long> ids) {
        Long userId = (Long) request.getAttribute("userId");
        billService.deleteBatch(userId, ids);
        return Result.success(i18nUtil.getMessage("bill.delete.success"));
    }

    @GetMapping("/recent")
    public Result<List<Bill>> recent(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(billService.getRecentBills(userId, 5));
    }

    @GetMapping("/page")
    public Result<IPage<Bill>> page(HttpServletRequest request,
                                     @RequestParam(required = false) Integer type,
                                     @RequestParam(required = false) Long categoryId,
                                     @RequestParam(required = false) String startDate,
                                     @RequestParam(required = false) String endDate,
                                     @RequestParam(required = false) BigDecimal minAmount,
                                     @RequestParam(required = false) BigDecimal maxAmount,
                                     @RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "10") int size) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(billService.pageList(userId, type, categoryId, startDate, endDate, minAmount, maxAmount, page, size));
    }

    // 以下原有接口保持不变
    @GetMapping("/statistics")
    public Result<Map<String, Object>> statistics(HttpServletRequest request,
                                                   @RequestParam String startDate,
                                                   @RequestParam String endDate) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(billService.getStatistics(userId, startDate, endDate));
    }

    @GetMapping("/category-stats")
    public Result<List<Map<String, Object>>> categoryStats(HttpServletRequest request,
                                                            @RequestParam Integer type,
                                                            @RequestParam String startDate,
                                                            @RequestParam String endDate) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(billService.getCategoryStats(userId, type, startDate, endDate));
    }

    @GetMapping("/month-stats")
    public Result<List<Map<String, Object>>> monthStats(HttpServletRequest request,
                                                         @RequestParam Integer type,
                                                         @RequestParam String startDate,
                                                         @RequestParam String endDate) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(billService.getMonthStats(userId, type, startDate, endDate));
    }

    @GetMapping("/year-stats")
    public Result<List<Map<String, Object>>> yearStats(HttpServletRequest request,
                                                        @RequestParam int year) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(billService.getYearStats(userId, year));
    }

    @GetMapping("/month-summary")
    public Result<List<Map<String, Object>>> monthSummary(HttpServletRequest request,
                                                           @RequestParam String startDate,
                                                           @RequestParam String endDate) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(billService.getMonthRangeSummary(userId, startDate, endDate));
    }
}
