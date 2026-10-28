package com.finance.service;

import com.finance.common.exception.BusinessException;
import com.finance.common.util.BillFileFormat;
import com.finance.entity.*;
import com.finance.entity.dto.BillDTO;
import com.finance.mapper.CategoryMapper;
import com.finance.mapper.FinanceAccountMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.multipart.MultipartFile;
import javax.validation.Validator;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class BillFileService {
    private static final long TTL=10*60*1000L;
    private final BillService bills;
    private final CategoryMapper categories;
    private final FinanceAccountMapper accounts;
    private final Validator validator;
    private final TransactionTemplate transaction;
    private final DataRevisionService revision;
    private final Map<String,Job> jobs=new LinkedHashMap<>();
    private static class Job {
        final Long owner;final long expires=System.currentTimeMillis()+TTL;
        final byte[] file;final List<Bill> rows;boolean done;
        Job(Long owner,byte[] file,List<Bill> rows){this.owner=owner;this.file=file;this.rows=rows;}
    }
    public BillFileService(BillService bills,CategoryMapper categories,FinanceAccountMapper accounts,Validator validator,PlatformTransactionManager manager,DataRevisionService revision){
        this.bills=bills;this.categories=categories;this.accounts=accounts;this.validator=validator;this.transaction=new TransactionTemplate(manager);
        this.revision=revision;
    }
    public Map<String,Object> export(Long owner,Integer type,Long categoryId,Long accountId,String start,String end,BigDecimal min,BigDecimal max){
        validateFilter(type,categoryId,accountId,start,end,min,max);
        com.baomidou.mybatisplus.core.metadata.IPage<Bill> page=bills.pageList(owner,type,categoryId,start,end,min,max,1,10000,accountId);
        if(page.getTotal()>10000)throw new BusinessException(400,"单次最多导出10000笔，请缩小筛选范围");
        return metadata(put(new Job(owner,BillFileFormat.xlsx(page.getRecords()),null)),page.getRecords().size());
    }
    public byte[] download(Long owner,String token){Job job=job(owner,token);synchronized(job){valid(job);if(job.file==null)throw new BusinessException(404,"导出文件不存在");return job.file;}}
    public Map<String,Object> preview(Long owner,MultipartFile file){
        if(file.isEmpty()||file.getSize()>2*1024*1024)throw new BusinessException(400,"CSV文件不能为空且不得超过2MB");
        String text;
        try{text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(file.getBytes())).toString();}
        catch(Exception error){throw new BusinessException(400,"请将CSV保存为UTF-8编码");}
        List<List<String>> parsed=BillFileFormat.csv(text);
        if(parsed.isEmpty()||!parsed.get(0).equals(Arrays.asList("type","categoryId","accountId","amount","billDate","remark")))throw new BusinessException(400,"表头不匹配，请下载通用CSV模板");
        List<Bill> validated=new ArrayList<>();List<Map<String,Object>> rows=new ArrayList<>();int errors=0;
        for(int index=1;index<parsed.size();index++){
            List<String> values=parsed.get(index);Map<String,Object> row=new LinkedHashMap<>();row.put("row",index+1);row.put("values",values);
            try {
                if(values.size()!=6)throw new BusinessException(400,"每行需要6列");
                BillDTO dto=new BillDTO();dto.setType(Integer.valueOf(values.get(0).trim()));dto.setCategoryId(Long.valueOf(values.get(1).trim()));
                dto.setAccountId(Long.valueOf(values.get(2).trim()));dto.setAmount(new BigDecimal(values.get(3).trim()));dto.setBillDate(values.get(4).trim());dto.setRemark(values.get(5));
                String message=validator.validate(dto).stream().map(v->v.getMessage()).sorted().collect(Collectors.joining("；"));
                if(!message.isEmpty())throw new BusinessException(400,message);
                Bill bill=new Bill();bill.setType(dto.getType());bill.setCategoryId(dto.getCategoryId());bill.setAccountId(dto.getAccountId());bill.setAmount(dto.getAmount());bill.setBillDate(LocalDate.parse(dto.getBillDate()));bill.setRemark(dto.getRemark());
                validateRow(owner,bill);validated.add(bill);row.put("error","");
            }catch(BusinessException error){row.put("error",error.getMessage());errors++;}
            catch(RuntimeException error){row.put("error","类型、ID、金额或日期格式无效");errors++;}
            rows.add(row);
        }
        if(rows.isEmpty())throw new BusinessException(400,"模板中没有账单数据");
        Map<String,Object> result=new LinkedHashMap<>();result.put("rows",rows);result.put("count",rows.size());result.put("errors",errors);
        if(errors==0)result.putAll(metadata(put(new Job(owner,null,validated)),validated.size()));return result;
    }
    public Map<String,Object> confirm(Long owner,String token){
        Job job=job(owner,token);
        // 同一确认令牌串行提交；只有事务提交完成才标记成功，重试不会再次入账。
        synchronized(job){valid(job);if(job.rows==null)throw new BusinessException(404,"导入预览不存在");
            if(!job.done){
                transaction.execute(status->{
                    revision.lock(owner);
                    // 分类可能由不同用户共享：跨CSV行必须采用全局相同的锁顺序。
                    TreeSet<Long> categoryIds=job.rows.stream().map(Bill::getCategoryId).collect(Collectors.toCollection(TreeSet::new));
                    for(Long id:categoryIds){
                        Category category=categories.selectActiveForUpdate(id);
                        if(category==null||!(owner.equals(category.getUserId())||Long.valueOf(0).equals(category.getUserId())))throw new BusinessException(400,"分类不存在或不可访问，请重新预校验");
                    }
                    for(Bill bill:job.rows){validateRow(owner,bill);bills.addBill(owner,bill);}
                    return null;
                });
                job.done=true;
            }
            return Collections.singletonMap("imported",job.rows.size());
        }
    }
    private void validateRow(Long owner,Bill bill){
        if(bill.getBillDate().getYear()<1900||bill.getBillDate().getYear()>2100)throw new BusinessException(400,"日期应在1900至2100年之间");
        Category category=categories.selectById(bill.getCategoryId());
        if(category==null||!(owner.equals(category.getUserId())||Long.valueOf(0).equals(category.getUserId()))||!bill.getType().equals(category.getType()))throw new BusinessException(400,"分类不存在、不属于本人或类型不匹配");
        FinanceAccount account=accounts.selectById(bill.getAccountId());
        if(account==null||!owner.equals(account.getUserId()))throw new BusinessException(400,"账户不存在或不属于本人");
        if(bill.getBillDate().isBefore(account.getOpeningDate()))throw new BusinessException(400,"账单日期早于账户期初日期");
    }
    private static void validateFilter(Integer type,Long category,Long account,String start,String end,BigDecimal min,BigDecimal max){
        try{if(type!=null&&(type<1||type>3)||category!=null&&category<=0||account!=null&&account<=0||min!=null&&min.signum()<0||max!=null&&max.signum()<0||min!=null&&max!=null&&min.compareTo(max)>0)throw new IllegalArgumentException();
            if(start!=null)LocalDate.parse(start);if(end!=null)LocalDate.parse(end);if(start!=null&&end!=null&&start.compareTo(end)>0)throw new IllegalArgumentException();
        }catch(RuntimeException error){throw new BusinessException(400,"导出筛选条件无效");}
    }
    private synchronized String put(Job job){
        jobs.values().removeIf(j->j.expires<=System.currentTimeMillis());
        long size=jobs.values().stream().filter(j->j.file!=null).mapToLong(j->j.file.length).sum();
        if(jobs.size()>=32||jobs.values().stream().filter(j->j.owner.equals(job.owner)).count()>=4||size+(job.file==null?0:job.file.length)>64*1024*1024)throw new BusinessException(429,"临时任务过多，请在10分钟后重试");
        String token=UUID.randomUUID().toString();jobs.put(token,job);return token;
    }
    private synchronized Job job(Long owner,String token){Job job=jobs.get(token);if(job==null)throw new BusinessException(404,"临时任务不存在，请重新生成");if(!job.owner.equals(owner))throw new BusinessException(403,"无权访问该临时任务");valid(job);return job;}
    private static void valid(Job job){if(job.expires<=System.currentTimeMillis())throw new BusinessException(410,"临时任务已过期，请重新生成");}
    private synchronized Map<String,Object> metadata(String token,int count){Job job=jobs.get(token);Map<String,Object> result=new LinkedHashMap<>();result.put("token",token);result.put("count",count);result.put("expiresAt",Instant.ofEpochMilli(job.expires).toString());return result;}
}
