package com.finance.controller;
import com.finance.common.result.Result;
import com.finance.service.NotificationService;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;
import java.util.Map;
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService service;
    public NotificationController(NotificationService service){this.service=service;}
    @GetMapping public Result<Map<String,Object>> list(HttpServletRequest request){return Result.success(service.list((Long)request.getAttribute("userId")));}
    @PutMapping("/preference") public Result<Void> preference(HttpServletRequest request,@RequestParam boolean enabled){service.preference((Long)request.getAttribute("userId"),enabled);return Result.success("success");}
    @PutMapping("/read-all") public Result<Void> readAll(HttpServletRequest request){service.readAll((Long)request.getAttribute("userId"));return Result.success("success");}
}
