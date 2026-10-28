package com.finance.controller;

import com.finance.common.result.Result;
import com.finance.service.AssetService;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@RequestMapping("/api/assets")
public class AssetController {
    private final AssetService assets;
    public AssetController(AssetService assets) { this.assets=assets; }
    @GetMapping("/snapshot") public Result<Map<String,Object>> snapshot(HttpServletRequest request) {
        return Result.success(assets.snapshot((Long)request.getAttribute("userId")));
    }
}
