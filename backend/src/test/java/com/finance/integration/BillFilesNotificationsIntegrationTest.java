package com.finance.integration;
import com.fasterxml.jackson.databind.JsonNode;
import com.finance.service.*;
import com.finance.common.exception.BusinessException;
import com.finance.entity.BillAttachment;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="app.recurring.enabled=false")
@EnabledIfEnvironmentVariable(named="FINANCE_INTEGRATION_DB_URL",matches=".+")
class BillFilesNotificationsIntegrationTest {
    @Autowired TestRestTemplate client;@Autowired JdbcTemplate jdbc;@Autowired BillFileService files;@Autowired AttachmentService attachments;@Autowired PlatformTransactionManager manager;
    @DynamicPropertySource static void database(DynamicPropertyRegistry p){String url=System.getenv("FINANCE_INTEGRATION_DB_URL");if(url==null||!url.matches("jdbc:mysql://[^/]+/finance_quality_test_[a-zA-Z0-9_]+(?:\\?.*)?"))throw new IllegalArgumentException("Isolated database required");p.add("spring.datasource.url",()->url);p.add("spring.datasource.username",()->"root");p.add("spring.datasource.password",()->System.getenv("FINANCE_INTEGRATION_DB_PASSWORD"));p.add("jwt.secret",()->"integration-test-only-secret-at-least-32-characters");p.add("app.avatar.storage-dir",()->"./target/integration-avatars");p.add("app.bill-files.storage-dir",()->"./target/integration-bill-files");}
    @BeforeEach void isolation(){assertTrue(jdbc.queryForObject("SELECT DATABASE()",String.class).startsWith("finance_quality_test_"));}
    @Test void previewNeverWritesReportsAllErrorsAndRejectsForeignAccounts(){Session a=session(),b=session();String csv=header()+"1,1,"+b.account+",12.34,"+today()+",foreign\r\n1,1,"+a.account+",-1,2026-02-30,bad\r\n";Map<String,Object> preview=files.preview(a.id,csv(csv));assertEquals(2,preview.get("errors"));assertNull(preview.get("token"));assertEquals(0,billCount(a));assertThrows(BusinessException.class,()->files.preview(a.id,csv("wrong,header")));}
    @Test void eightConfirmRetriesImportOnceAndQuotedRemarksSurvive()throws Exception{Session a=session(),b=session();String token=String.valueOf(files.preview(a.id,csv(header()+"1,1,"+a.account+",12.34,"+today()+",\"comma, \"\"quote\"\"\nnewline\"\r\n")).get("token"));assertEquals(0,billCount(a));assertEquals(403,assertThrows(BusinessException.class,()->files.confirm(b.id,token)).getCode());ExecutorService pool=Executors.newFixedThreadPool(8);try{List<Future<Map<String,Object>>> results=new ArrayList<>();for(int i=0;i<8;i++)results.add(pool.submit(()->files.confirm(a.id,token)));for(Future<Map<String,Object>> f:results)assertEquals(1,f.get(30,TimeUnit.SECONDS).get("imported"));}finally{pool.shutdownNow();}assertEquals(1,billCount(a));assertEquals("comma, \"quote\"\nnewline",jdbc.queryForObject("SELECT remark FROM bill WHERE user_id=?",String.class,a.id));}
    @Test void changedReferencesRollbackWholeImportAndExpiredTokensReject(){Session a=session();ok(call(a,HttpMethod.POST,"/api/account",fields("name","Second","accountType","BANK","openingBalance","0","openingDate","2020-01-01")));long second=jdbc.queryForObject("SELECT id FROM finance_account WHERE user_id=? AND name='Second'",Long.class,a.id);String token=String.valueOf(files.preview(a.id,csv(header()+"1,1,"+a.account+",1,"+today()+",first\n1,1,"+second+",2,"+today()+",second\n")).get("token"));jdbc.update("UPDATE finance_account SET deleted=1 WHERE id=?",second);assertThrows(BusinessException.class,()->files.confirm(a.id,token));assertEquals(0,billCount(a));Map<?,?> jobs=(Map<?,?>)ReflectionTestUtils.getField(files,"jobs");ReflectionTestUtils.setField(jobs.get(token),"expires",0L);assertEquals(410,assertThrows(BusinessException.class,()->files.confirm(a.id,token)).getCode());}
    @Test void exportIsOwnerFilteredAuthenticZipAndExpiryEnforced(){Session a=session(),b=session();bill(a,"12.34");bill(b,"99");Map<String,Object> exported=files.export(a.id,1,null,null,null,null,null,null);String token=String.valueOf(exported.get("token"));assertEquals(1,exported.get("count"));byte[] content=files.download(a.id,token);assertEquals('P',content[0]);assertEquals('K',content[1]);assertEquals(403,assertThrows(BusinessException.class,()->files.download(b.id,token)).getCode());Map<?,?> jobs=(Map<?,?>)ReflectionTestUtils.getField(files,"jobs");ReflectionTestUtils.setField(jobs.get(token),"expires",0L);assertEquals(410,assertThrows(BusinessException.class,()->files.download(a.id,token)).getCode());}
    @Test void receiptChecksImageOwnershipRollbackAndDeletedVisibility()throws Exception{Session a=session(),b=session();long bill=bill(a,"2");ByteArrayOutputStream png=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(4,4,BufferedImage.TYPE_INT_RGB),"png",png);MockMultipartFile image=new MockMultipartFile("file","receipt.png","image/png",png.toByteArray());assertThrows(BusinessException.class,()->attachments.upload(a.id,bill,new MockMultipartFile("file","fake.png","image/png","not an image".getBytes(StandardCharsets.UTF_8))));BillAttachment item=attachments.upload(a.id,bill,image);assertTrue(attachments.resource(item).exists());assertEquals(403,assertThrows(BusinessException.class,()->attachments.get(b.id,bill,item.getId())).getCode());final Path[] rolled={null};new TransactionTemplate(manager).execute(status->{BillAttachment temp=attachments.upload(a.id,bill,image);try{rolled[0]=attachments.resource(temp).getFile().toPath();}catch(IOException e){throw new RuntimeException(e);}status.setRollbackOnly();return null;});assertFalse(Files.exists(rolled[0]));attachments.delete(a.id,bill,item.getId());assertEquals(404,assertThrows(BusinessException.class,()->attachments.get(a.id,bill,item.getId())).getCode());}
    @Test void budgetAlertsDeduplicateRespectPreferenceAndStayPrivate(){Session a=session(),b=session();ok(call(a,HttpMethod.POST,"/api/budget",fields("amount","100","month",today().substring(0,7))));bill(a,"80");assertEquals(1,notices(a).path("unread").asInt());bill(a,"25");assertEquals(2,notices(a).path("unread").asInt());bill(a,"1");assertEquals(2,notices(a).path("unread").asInt());assertEquals(0,notices(b).path("items").size());ok(call(a,HttpMethod.PUT,"/api/notifications/read-all",null));assertEquals(0,notices(a).path("unread").asInt());ok(call(b,HttpMethod.PUT,"/api/notifications/preference?enabled=false",null));ok(call(b,HttpMethod.POST,"/api/budget",fields("amount","10","month",today().substring(0,7))));bill(b,"20");assertEquals(0,notices(b).path("items").size());assertFalse(notices(b).path("enabled").asBoolean());}
    private JsonNode notices(Session s){JsonNode r=call(s,HttpMethod.GET,"/api/notifications",null);ok(r);return r.path("data");}
    private int billCount(Session s){return jdbc.queryForObject("SELECT COUNT(*) FROM bill WHERE user_id=?",Integer.class,s.id);}
    private Session session(){Session s=new Session();String name="p3_"+UUID.randomUUID().toString().substring(0,12);ok(call(null,HttpMethod.POST,"/api/user/register",fields("username",name,"password","Test123!")));JsonNode r=call(null,HttpMethod.POST,"/api/user/login",fields("username",name,"password","Test123!"));ok(r);s.id=r.path("data").path("userId").asLong();s.token=r.path("data").path("token").asText();ok(call(s,HttpMethod.POST,"/api/account",fields("name","Cash","accountType","CASH","openingBalance","100","openingDate","2020-01-01")));s.account=jdbc.queryForObject("SELECT id FROM finance_account WHERE user_id=?",Long.class,s.id);return s;}
    private long bill(Session s,String amount){ok(call(s,HttpMethod.POST,"/api/bill",fields("accountId",s.account,"categoryId",1,"type",1,"amount",amount,"billDate",today())));return jdbc.queryForObject("SELECT MAX(id) FROM bill WHERE user_id=?",Long.class,s.id);}
    private JsonNode call(Session s,HttpMethod method,String path,Object body){HttpHeaders h=new HttpHeaders();h.setContentType(MediaType.APPLICATION_JSON);if(s!=null)h.setBearerAuth(s.token);return client.exchange(path,method,new HttpEntity<>(body,h),JsonNode.class).getBody();}
    private void ok(JsonNode r){assertNotNull(r);assertEquals(200,r.path("code").asInt(),r.toString());}
    private String today(){return RecurringService.today().toString();}
    private String header(){return "type,categoryId,accountId,amount,billDate,remark\r\n";}
    private MockMultipartFile csv(String text){return new MockMultipartFile("file","bills.csv","text/csv",text.getBytes(StandardCharsets.UTF_8));}
    private static Map<String,Object> fields(Object...p){Map<String,Object> r=new HashMap<>();for(int i=0;i<p.length;i+=2)r.put((String)p[i],p[i+1]);return r;}
    private static class Session{long id,account;String token;}
}
