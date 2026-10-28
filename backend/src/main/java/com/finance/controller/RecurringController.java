package com.finance.controller;

import com.finance.common.result.Result;
import com.finance.entity.dto.RecurringDTO;
import com.finance.service.RecurringService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@RequestMapping("/api/recurring")
public class RecurringController {
    private final RecurringService recurring;
    public RecurringController(RecurringService recurring) { this.recurring = recurring; }
    @GetMapping("/list") public Result<Map<String,Object>> list(HttpServletRequest request) {
        return Result.success(recurring.list((Long) request.getAttribute("userId")));
    }
    @PostMapping public Result<Void> create(HttpServletRequest request, @Validated @RequestBody RecurringDTO dto) {
        recurring.create((Long) request.getAttribute("userId"), dto); return Result.success("success");
    }
    @PutMapping("/{id}") public Result<Void> update(HttpServletRequest request, @PathVariable Long id, @Validated @RequestBody RecurringDTO dto) {
        recurring.update((Long) request.getAttribute("userId"), id, dto); return Result.success("success");
    }
    @PutMapping("/{id}/status") public Result<Void> status(HttpServletRequest request, @PathVariable Long id, @RequestParam boolean enabled) {
        recurring.setEnabled((Long) request.getAttribute("userId"), id, enabled); return Result.success("success");
    }
    @DeleteMapping("/{id}") public Result<Void> delete(HttpServletRequest request, @PathVariable Long id) {
        recurring.delete((Long) request.getAttribute("userId"), id); return Result.success("success");
    }
    @PostMapping("/run") public Result<Map<String,Integer>> run(HttpServletRequest request) {
        return Result.success(recurring.runDue((Long) request.getAttribute("userId"), RecurringService.today()));
    }
}
