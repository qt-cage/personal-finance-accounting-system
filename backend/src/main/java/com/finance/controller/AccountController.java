package com.finance.controller;

import com.finance.common.result.Result;
import com.finance.entity.FinanceAccount;
import com.finance.entity.dto.AccountDTO;
import com.finance.service.AccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/api/account")
public class AccountController {
    @Autowired private AccountService accountService;

    @GetMapping("/list")
    public Result<List<FinanceAccount>> list(HttpServletRequest request) {
        return Result.success(accountService.list((Long) request.getAttribute("userId")));
    }

    @PostMapping
    public Result<Void> create(HttpServletRequest request, @Validated @RequestBody AccountDTO dto) {
        accountService.create((Long) request.getAttribute("userId"), dto);
        return Result.success("success");
    }

    @PutMapping("/{id}")
    public Result<Void> update(HttpServletRequest request, @PathVariable Long id, @Validated @RequestBody AccountDTO dto) {
        accountService.update((Long) request.getAttribute("userId"), id, dto);
        return Result.success("success");
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(HttpServletRequest request, @PathVariable Long id) {
        accountService.delete((Long) request.getAttribute("userId"), id);
        return Result.success("success");
    }
}
