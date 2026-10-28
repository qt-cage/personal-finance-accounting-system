package com.finance.controller;

import com.finance.common.result.Result;
import com.finance.service.BillFileService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/bill/files")
public class BillFileController {
    private final BillFileService files;
    public BillFileController(BillFileService files){this.files=files;}
    @PostMapping("/export")
    public Result<Map<String,Object>> export(HttpServletRequest request,@RequestParam(required=false)Integer type,@RequestParam(required=false)Long categoryId,@RequestParam(required=false)Long accountId,@RequestParam(required=false)String startDate,@RequestParam(required=false)String endDate,@RequestParam(required=false)BigDecimal minAmount,@RequestParam(required=false)BigDecimal maxAmount){return Result.success(files.export(owner(request),type,categoryId,accountId,startDate,endDate,minAmount,maxAmount));}
    @GetMapping("/download/{token}")
    public ResponseEntity<byte[]> download(HttpServletRequest request,@PathVariable String token){return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"no-store").header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename("个人账单.xlsx",StandardCharsets.UTF_8).build().toString()).contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).body(files.download(owner(request),token));}
    @GetMapping("/template")
    public ResponseEntity<byte[]> template(){return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"no-store").header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=bill-template.csv").contentType(MediaType.parseMediaType("text/csv;charset=UTF-8")).body("\uFEFFtype,categoryId,accountId,amount,billDate,remark\r\n".getBytes(StandardCharsets.UTF_8));}
    @PostMapping("/import/preview")
    public Result<Map<String,Object>> preview(HttpServletRequest request,@RequestParam MultipartFile file){return Result.success(files.preview(owner(request),file));}
    @PostMapping("/import/confirm/{token}")
    public Result<Map<String,Object>> confirm(HttpServletRequest request,@PathVariable String token){return Result.success(files.confirm(owner(request),token));}
    private Long owner(HttpServletRequest request){return (Long)request.getAttribute("userId");}
}
