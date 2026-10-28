package com.finance.controller;

import com.finance.common.result.Result;
import com.finance.entity.BillAttachment;
import com.finance.service.AttachmentService;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/bill/{billId}/attachments")
public class AttachmentController {
    private final AttachmentService files;
    public AttachmentController(AttachmentService files){this.files=files;}
    @GetMapping public Result<List<BillAttachment>> list(HttpServletRequest r,@PathVariable Long billId){return Result.success(files.list((Long)r.getAttribute("userId"),billId));}
    @PostMapping public Result<BillAttachment> upload(HttpServletRequest r,@PathVariable Long billId,@RequestParam("file") MultipartFile file){return Result.success(files.upload((Long)r.getAttribute("userId"),billId,file));}
    @GetMapping("/{id}") public ResponseEntity<Resource> get(HttpServletRequest r,@PathVariable Long billId,@PathVariable Long id){
        BillAttachment item=files.get((Long)r.getAttribute("userId"),billId,id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(item.getMediaType())).cacheControl(CacheControl.noStore())
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.inline().filename(item.getFilename(),StandardCharsets.UTF_8).build().toString()).body(files.resource(item));
    }
    @DeleteMapping("/{id}") public Result<Void> delete(HttpServletRequest r,@PathVariable Long billId,@PathVariable Long id){files.delete((Long)r.getAttribute("userId"),billId,id);return Result.success("success");}
}
