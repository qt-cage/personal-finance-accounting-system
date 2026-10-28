package com.finance.integration;

import com.finance.common.exception.BusinessException;
import com.finance.entity.BillAttachment;
import com.finance.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"app.recurring.enabled=false", "app.bill-files.max-user-storage-bytes=100"})
@EnabledIfEnvironmentVariable(named="FINANCE_INTEGRATION_DB_URL", matches=".+")
class EngineeringRiskIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired RecurringService recurring;
    @Autowired AttachmentService attachments;
    @Autowired BillFileService files;
    @Autowired BillService bills;

    @Test void startupRejectsMissingColumnTableAndUniqueIndexInDisposableEmptySchema() throws Exception {
        String original=jdbc.queryForObject("SELECT DATABASE()",String.class);
        String schema="finance_quality_test_schema_"+UUID.randomUUID().toString().replace("-", "").substring(0,12);
        jdbc.execute("CREATE DATABASE `"+schema+"`");
        try {
            for(String table:jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema=?",String.class,original)){
                assertTrue(table.matches("[a-z_]+"));
                jdbc.execute("CREATE TABLE `"+schema+"`.`"+table+"` LIKE `"+original+"`.`"+table+"`");
            }
            String url=System.getenv("FINANCE_INTEGRATION_DB_URL").replace("/"+original,"/"+schema);
            org.springframework.jdbc.datasource.DriverManagerDataSource source=new org.springframework.jdbc.datasource.DriverManagerDataSource(url,"root",System.getenv("FINANCE_INTEGRATION_DB_PASSWORD"));
            com.finance.config.DatabaseStartupCheck check=new com.finance.config.DatabaseStartupCheck(source);
            check.run(null);
            jdbc.execute("ALTER TABLE `"+schema+"`.`user` DROP COLUMN role");
            assertTrue(assertThrows(IllegalStateException.class,()->check.run(null)).getMessage().contains("user columns"));
            jdbc.execute("ALTER TABLE `"+schema+"`.`user` ADD COLUMN role VARCHAR(10) NOT NULL DEFAULT 'USER'");
            jdbc.execute("ALTER TABLE `"+schema+"`.`bill` DROP INDEX uk_bill_request");
            assertTrue(assertThrows(IllegalStateException.class,()->check.run(null)).getMessage().contains("unique constraint bill"));
            jdbc.execute("ALTER TABLE `"+schema+"`.`bill` ADD UNIQUE KEY uk_bill_request(user_id,request_id)");
            jdbc.execute("DROP TABLE `"+schema+"`.`data_revision`");
            assertTrue(assertThrows(IllegalStateException.class,()->check.run(null)).getMessage().contains("data_revision columns"));
        } finally {
            // 仅回收本测试刚创建的空结构副本，不触及任何已有库。
            jdbc.execute("DROP DATABASE `"+schema+"`");
        }
    }
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        String url=System.getenv("FINANCE_INTEGRATION_DB_URL");
        if(url==null||!url.matches("jdbc:mysql://[^/]+/finance_quality_test_[a-zA-Z0-9_]+(?:\\?.*)?"))throw new IllegalArgumentException("Isolated database required");
        p.add("spring.datasource.url",()->url);p.add("spring.datasource.username",()->"root");
        p.add("spring.datasource.password",()->System.getenv("FINANCE_INTEGRATION_DB_PASSWORD"));
        p.add("jwt.secret",()->"integration-test-only-secret-at-least-32-characters");
        p.add("app.bill-files.storage-dir",()->"./target/engineering-test-files");
    }
    @BeforeEach void isolated(){assertTrue(jdbc.queryForObject("SELECT DATABASE()",String.class).startsWith("finance_quality_test_"));}

    @Test void concurrentReclassificationKeepsSummaryInOneSnapshot() throws Exception {
        long owner=user(), id=bill(owner);
        jdbc.update("UPDATE bill SET amount=100 WHERE id=?",id);
        ExecutorService pool=Executors.newSingleThreadExecutor();
        try {
            Future<?> writer=pool.submit(()->{for(int i=0;i<100;i++)jdbc.update("UPDATE bill SET type=? WHERE id=?",i%2+1,id);});
            String date=RecurringService.today().toString();
            for(int i=0;i<100;i++){
                Map<String,Object> summary=bills.getStatistics(owner,date,date);
                java.math.BigDecimal total=((java.math.BigDecimal)summary.get("income")).add((java.math.BigDecimal)summary.get("expense"));
                assertEquals(0,new java.math.BigDecimal("100").compareTo(total));
            }
            writer.get(15,TimeUnit.SECONDS);
        } finally {pool.shutdownNow();}
    }

    @Test void persistentFirstHundredFailuresDoNotStarveLaterDuePlan() {
        long owner=user(), account=account(owner); LocalDate date=RecurringService.today();
        for(int i=0;i<101;i++)jdbc.update("INSERT INTO recurring_bill(user_id,name,type,category_id,account_id,amount,frequency,start_date,next_date) VALUES(?,?,1,?,?,1,'DAILY',?,?)",owner,"fairness-"+i,i==100?1:Long.MAX_VALUE,account,date,date);
        assertEquals(0,recurring.runDue(owner,date).get("generated"));
        assertEquals(1,recurring.runDue(owner,date).get("generated"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM bill WHERE user_id=?",Integer.class,owner));
    }

    @Test void deletedAttachmentsStillCountTowardRetainedStorageQuota() throws Exception {
        long owner=user(), bill=bill(owner); MockMultipartFile image=image();
        assertTrue(image.getSize()<100 && image.getSize()*2>100);
        BillAttachment first=attachments.upload(owner,bill,image);attachments.delete(owner,bill,first.getId());
        assertEquals(409,assertThrows(BusinessException.class,()->attachments.upload(owner,bill,image)).getCode());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM bill_attachment WHERE user_id=?",Integer.class,owner));
    }

    @Test void concurrentAttachmentsOnDifferentBillsRespectOneOwnerQuota() throws Exception {
        long owner=user(), firstBill=bill(owner), secondBill=bill(owner); MockMultipartFile image=image();
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        try {
            Future<Integer> a=pool.submit(()->uploadResult(start,owner,firstBill,image));
            Future<Integer> b=pool.submit(()->uploadResult(start,owner,secondBill,image));start.countDown();
            List<Integer> result=Arrays.asList(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS));
            assertEquals(1,Collections.frequency(result,200));assertEquals(1,Collections.frequency(result,409));
            assertTrue(jdbc.queryForObject("SELECT SUM(size) FROM bill_attachment WHERE user_id=?",Long.class,owner)<=100);
        } finally {pool.shutdownNow();}
    }

    @Test void oppositeCsvCategoryOrdersCommitForBothOwners() throws Exception {
        long a=user(),b=user(),firstAccount=account(a),secondAccount=account(b);
        String date=RecurringService.today().toString();
        String tokenA=preview(a,firstAccount,date,1,2),tokenB=preview(b,secondAccount,date,2,1);
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        try {
            Future<?> first=pool.submit(()->{await(start);files.confirm(a,tokenA);});
            Future<?> second=pool.submit(()->{await(start);files.confirm(b,tokenB);});start.countDown();
            first.get(20,TimeUnit.SECONDS);second.get(20,TimeUnit.SECONDS);
            assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM bill WHERE user_id=?",Integer.class,a));
            assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM bill WHERE user_id=?",Integer.class,b));
        } finally {pool.shutdownNow();}
    }
    private String preview(long owner,long account,String date,int first,int second) {
        String csv="type,categoryId,accountId,amount,billDate,remark\n1,"+first+","+account+",1,"+date+",first\n1,"+second+","+account+",1,"+date+",second\n";
        return (String)files.preview(owner,new MockMultipartFile("file","bills.csv","text/csv",csv.getBytes(java.nio.charset.StandardCharsets.UTF_8))).get("token");
    }
    private int uploadResult(CountDownLatch start,long owner,long bill,MockMultipartFile file){await(start);try{attachments.upload(owner,bill,file);return 200;}catch(BusinessException e){return e.getCode();}}
    private static void await(CountDownLatch start){try{start.await();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}}
    private long user(){String name="risk_"+UUID.randomUUID().toString().substring(0,12);jdbc.update("INSERT INTO `user`(username,password) VALUES(?,?)",name,"unused-test-value");return jdbc.queryForObject("SELECT id FROM `user` WHERE username=?",Long.class,name);}
    private long account(long owner){jdbc.update("INSERT INTO finance_account(user_id,name,account_type,opening_balance,opening_date) VALUES(?,'Test','CASH',0,'2020-01-01')",owner);return jdbc.queryForObject("SELECT id FROM finance_account WHERE user_id=?",Long.class,owner);}
    private long bill(long owner){jdbc.update("INSERT INTO bill(user_id,category_id,amount,type,bill_date) VALUES(?,1,1,1,?)",owner,RecurringService.today());return jdbc.queryForObject("SELECT MAX(id) FROM bill WHERE user_id=?",Long.class,owner);}
    private MockMultipartFile image() throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(8,8,BufferedImage.TYPE_INT_RGB),"png",out);return new MockMultipartFile("file","test.png","image/png",out.toByteArray());}
}
