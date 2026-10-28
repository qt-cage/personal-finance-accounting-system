package com.finance.controller;
import com.finance.common.result.Result;
import com.finance.service.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
@RequestMapping("/api/system")
public class SystemController {
    private final DictionaryService dictionaries;private final SystemSettingsService settings;
    public SystemController(DictionaryService dictionaries,SystemSettingsService settings){this.dictionaries=dictionaries;this.settings=settings;}
    @GetMapping("/dictionaries") public Result<List<Map<String,Object>>> dictionaries(){return Result.success(dictionaries.list());}
    @GetMapping("/settings") public Result<Map<String,Object>> settings(){return Result.success(settings.read());}
}
