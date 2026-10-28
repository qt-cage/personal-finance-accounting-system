package com.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.finance.common.exception.BusinessException;
import com.finance.entity.Bill;
import com.finance.entity.BillAttachment;
import com.finance.mapper.AttachmentMapper;
import com.finance.mapper.BillMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;

@Service
public class AttachmentService {
    private final AttachmentMapper attachments;
    private final BillMapper bills;
    private final AvatarStorageService validator;
    private final Path directory;
    private final DataRevisionService revision;
    private final long maxUserStorageBytes;
    public AttachmentService(AttachmentMapper attachments,BillMapper bills,AvatarStorageService validator,
                             @Value("${app.bill-files.storage-dir:./uploads/bill-files}") String directory,
                             DataRevisionService revision,
                             @Value("${app.bill-files.max-user-storage-bytes:104857600}") long maxUserStorageBytes) {
        this.attachments=attachments;this.bills=bills;this.validator=validator;
        this.directory=Paths.get(directory).toAbsolutePath().normalize();
        if(maxUserStorageBytes<=0)throw new IllegalArgumentException("Bill file storage quota must be positive");
        this.revision=revision;this.maxUserStorageBytes=maxUserStorageBytes;
    }
    public List<BillAttachment> list(Long userId,Long billId) {
        ownBill(userId,billId);
        return attachments.selectList(new LambdaQueryWrapper<BillAttachment>().eq(BillAttachment::getUserId,userId).eq(BillAttachment::getBillId,billId));
    }
    @Transactional
    public BillAttachment upload(Long userId,Long billId,MultipartFile file) {
        revision.lock(userId);
        Bill bill=bills.selectActiveForUpdate(billId);
        if(bill==null)throw new BusinessException(404,"账单不存在");
        if(!userId.equals(bill.getUserId()))throw new BusinessException(403,"无权操作该账单");
        if(attachments.selectCount(new LambdaQueryWrapper<BillAttachment>().eq(BillAttachment::getBillId,billId))>=5)throw new BusinessException(400,"每笔账单最多保存5张小票");
        String extension;
        try{extension=validator.validate(file);}catch(BusinessException error){throw new BusinessException(error.getCode(),"请选择不超过2MB、4096×4096的有效JPG或PNG小票图片");}
        if(file.getSize()>maxUserStorageBytes-attachments.retainedBytes(userId))throw new BusinessException(409,"小票保留空间已达上限，请联系管理员处理保留文件");
        BillAttachment item=new BillAttachment();item.setUserId(userId);item.setBillId(billId);
        item.setStorageName(UUID.randomUUID().toString().replace("-","")+"."+extension);
        String original=Objects.toString(file.getOriginalFilename(),"receipt."+extension).replaceAll("[\\r\\n\\\\/]","_");
        item.setFilename(original.substring(0,Math.min(original.length(),100)));
        item.setMediaType("png".equals(extension)?"image/png":"image/jpeg");item.setSize(file.getSize());
        Path target=path(item);
        try(InputStream input=file.getInputStream()){Files.createDirectories(directory);Files.copy(input,target);}
        catch(IOException error){
            try{Files.deleteIfExists(target);}catch(IOException ignored){}
            throw new BusinessException(500,"小票保存失败，请稍后重试");
        }
        // 仅回收本次生成的随机文件；数据库事务失败不会遗留可访问附件。
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
            @Override public void afterCompletion(int status){if(status!=STATUS_COMMITTED)try{Files.deleteIfExists(target);}catch(IOException ignored){}}
        });
        attachments.insert(item);return item;
    }
    public BillAttachment get(Long userId,Long billId,Long id) {
        ownBill(userId,billId);BillAttachment item=attachments.selectById(id);
        if(item==null)throw new BusinessException(404,"小票不存在");
        if(!userId.equals(item.getUserId())||!billId.equals(item.getBillId()))throw new BusinessException(403,"无权读取该小票");
        return item;
    }
    public Resource resource(BillAttachment item) {
        Path path=path(item);
        if(!Files.isRegularFile(path)||Files.isSymbolicLink(path))throw new BusinessException(404,"小票文件不存在");
        return new FileSystemResource(path);
    }
    public void delete(Long userId,Long billId,Long id){get(userId,billId,id);attachments.deleteById(id);}
    private Path path(BillAttachment item) {
        if(!item.getStorageName().matches("[a-f0-9]{32}\\.(jpg|png)"))throw new BusinessException(400,"小票路径无效");
        Path path=directory.resolve(item.getStorageName()).normalize();
        if(!directory.equals(path.getParent()))throw new BusinessException(400,"小票路径无效");
        return path;
    }
    private void ownBill(Long userId,Long id) {
        Bill bill=bills.selectById(id);
        if(bill==null)throw new BusinessException(404,"账单不存在");
        if(!userId.equals(bill.getUserId()))throw new BusinessException(403,"无权操作该账单");
    }
}
