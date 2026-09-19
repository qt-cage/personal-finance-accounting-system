package com.finance.controller;

import com.finance.common.i18n.I18nUtil;
import com.finance.common.result.Result;
import com.finance.entity.Budget;
import com.finance.entity.dto.BudgetDTO;
import com.finance.service.BudgetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/api/budget")
public class BudgetController {

    @Autowired
    private BudgetService budgetService;

    @Autowired
    private I18nUtil i18nUtil;

    @GetMapping("/list")
    public Result<List<Budget>> list(HttpServletRequest request, @RequestParam String month) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(budgetService.listByMonth(userId, month));
    }

    @PostMapping
    public Result<Void> save(HttpServletRequest request, @Validated @RequestBody BudgetDTO dto) {
        Long userId = (Long) request.getAttribute("userId");
        Budget budget = new Budget();
        budget.setCategoryId(dto.getCategoryId());
        budget.setAmount(dto.getAmount());
        budget.setMonth(dto.getMonth());
        budgetService.saveBudget(userId, budget);
        return Result.success(i18nUtil.getMessage("budget.set.success"));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(HttpServletRequest request, @PathVariable Long id) {
        Long userId = (Long) request.getAttribute("userId");
        budgetService.deleteBudget(id, userId);
        return Result.success(i18nUtil.getMessage("budget.delete.success"));
    }
}
