package com.finance.integration;
import com.fasterxml.jackson.databind.JsonNode;
import com.finance.common.exception.BusinessException;
import com.finance.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"app.recurring.enabled=false","app.cache.enabled=true","app.cache.namespace=finance_quality_test_b5_integration"})
@EnabledIfEnvironmentVariable(named="FINANCE_INTEGRATION_REDIS_PORT",matches="[0-9]{4,5}")
@EnabledIfEnvironmentVariable(named="FINANCE_INTEGRATION_DB_URL",matches=".+")
class RedisAggregateIntegrationTest {
    @Test void versionChangeWhileLoadingDoesNotPublishUnderPreviousVersion() {
        Session s=session();account(s,"Race account","CASH","0");long before=revision(s);
        String value=cache.get(s.id,"version-race",new com.fasterxml.jackson.core.type.TypeReference<String>(){},()->{
            jdbc.update("UPDATE data_revision SET revision=revision+1 WHERE user_id=?",s.id);
            return "after-commit";
        });
        assertEquals("after-commit",value);
        assertTrue(redis.keys("finance_quality_test_b5_integration:"+s.id+":"+before+":*").isEmpty());
    }
    @Autowired TestRestTemplate client;@Autowired JdbcTemplate jdbc;@Autowired AggregateCacheService cache;@Autowired StringRedisTemplate redis;@Autowired BillFileService files;
    @DynamicPropertySource static void database(DynamicPropertyRegistry p){String url=System.getenv("FINANCE_INTEGRATION_DB_URL");if(url==null||!url.matches("jdbc:mysql://[^/]+/finance_quality_test_[a-zA-Z0-9_]+(?:\\?.*)?"))throw new IllegalArgumentException("Isolated database required");p.add("spring.datasource.url",()->url);p.add("spring.datasource.username",()->"root");p.add("spring.datasource.password",()->System.getenv("FINANCE_INTEGRATION_DB_PASSWORD"));p.add("jwt.secret",()->"integration-test-only-secret-at-least-32-characters");p.add("spring.redis.host",()->"127.0.0.1");p.add("spring.redis.port",()->Integer.parseInt(System.getenv("FINANCE_INTEGRATION_REDIS_PORT")));p.add("spring.redis.password",()->"");p.add("app.avatar.storage-dir",()->"./target/integration-avatars");}
    @BeforeEach void isolation() throws Exception {
        assertTrue(jdbc.queryForObject("SELECT DATABASE()",String.class).startsWith("finance_quality_test_"));
        Properties config=redis.execute((RedisCallback<Properties>)c->c.serverCommands().getConfig("dir"));
        String actual=config.getProperty("dir").replace('\\','/'), expected=System.getenv("FINANCE_INTEGRATION_REDIS_DIR");
        if(expected==null){
            assertEquals("16381",System.getenv("FINANCE_INTEGRATION_REDIS_PORT"));
            assertTrue(actual.contains("taskbook-b5-20261009/redis-data"));
        }else{
            assertTrue(java.nio.file.Paths.get(expected).getFileName().toString().startsWith("finance_quality_test_"));
            if(actual.startsWith("/cygdrive/"))actual=actual.substring(10,11)+":"+actual.substring(11);
            assertEquals(java.nio.file.Paths.get(expected).toRealPath(),java.nio.file.Paths.get(actual).toRealPath());
        }
    }
    @Test void actualRedisHitsStayPrivateAndBillWritesInvalidateSummary(){Session a=session(),b=session();long account=account(a,"Cash","CASH","100");money("0",summary(a).path("expense"));long before=cache.metrics().get("hits");money("0",summary(a).path("expense"));assertTrue(cache.metrics().get("hits")>before);Set<String> keys=redis.keys("finance_quality_test_b5_integration:"+a.id+":*");assertNotNull(keys);assertFalse(keys.isEmpty());for(String key:keys)assertTrue(redis.getExpire(key)>0);long bill=bill(a,account,1,"12.34");money("12.34",summary(a).path("expense"));money("0",summary(b).path("expense"));ok(call(a,HttpMethod.PUT,"/api/bill",fields("id",bill,"accountId",account,"categoryId",1,"type",1,"amount","15.67","billDate",today())));money("15.67",summary(a).path("expense"));ok(call(a,HttpMethod.DELETE,"/api/bill/"+bill,null));money("0",summary(a).path("expense"));}
    @Test void accountTransferRenameAndPlanReferencesInvalidateAssetAndAccountCaches(){Session a=session();long cash=account(a,"Cash","CASH","100"),card=account(a,"Card","CREDIT","-20");money("80",assets(a).path("netAssets"));assets(a);ok(call(a,HttpMethod.POST,"/api/bill/transfer",fields("accountId",cash,"transferAccountId",card,"amount","10","billDate",today(),"requestId",UUID.randomUUID().toString())));JsonNode snapshot=assets(a);money("90",snapshot.path("assets"));money("10",snapshot.path("liabilities"));ok(call(a,HttpMethod.PUT,"/api/account/"+cash,fields("name","Renamed cash","accountType","CASH","openingBalance","100","openingDate","2020-01-01")));assertTrue(assets(a).path("accounts").toString().contains("Renamed cash"));accounts(a);ok(call(a,HttpMethod.POST,"/api/recurring",fields("name","Plan","type",1,"categoryId",1,"accountId",cash,"amount","1","frequency","DAILY","startDate",today())));assertTrue(findAccount(accounts(a),cash).path("hasPlans").asBoolean());long id=jdbc.queryForObject("SELECT id FROM recurring_bill WHERE user_id=?",Long.class,a.id);ok(call(a,HttpMethod.DELETE,"/api/recurring/"+id,null));assertFalse(findAccount(accounts(a),cash).path("hasPlans").asBoolean());}
    @Test void privateAndSharedCategoryRenamesInvalidateCategoryAggregates(){Session a=session(),admin=session();long account=account(a,"Cash","CASH","100");String name="private_"+UUID.randomUUID().toString().substring(0,8);ok(call(a,HttpMethod.POST,"/api/category",fields("name",name,"type",1)));long own=jdbc.queryForObject("SELECT id FROM category WHERE user_id=?",Long.class,a.id);bill(a,account,own,"1");categoryStats(a);ok(call(a,HttpMethod.PUT,"/api/category",fields("id",own,"name","Private renamed","type",1,"sortOrder",1)));assertTrue(categoryStats(a).toString().contains("Private renamed"));jdbc.update("UPDATE `user` SET `role`='ADMIN' WHERE id=?",admin.id);String shared="shared_"+UUID.randomUUID().toString().substring(0,8);ok(call(admin,HttpMethod.POST,"/api/admin/default-categories",fields("name",shared,"type",1)));long id=jdbc.queryForObject("SELECT id FROM category WHERE user_id=0 AND name=?",Long.class,shared);bill(a,account,id,"2");categoryStats(a);ok(call(admin,HttpMethod.PUT,"/api/admin/default-categories",fields("id",id,"name","Shared renamed "+id,"type",1,"sortOrder",1)));assertTrue(categoryStats(a).toString().contains("Shared renamed "+id));}
    @Test void failedWritesAndImportRollbackAlsoRollbackRevisions(){Session a=session(),b=session();long cash=account(a,"Cash","CASH","100"),second=account(a,"Second","BANK","0");ok(call(b,HttpMethod.POST,"/api/category",fields("name","Foreign","type",1)));long foreign=jdbc.queryForObject("SELECT id FROM category WHERE user_id=?",Long.class,b.id);summary(a);long version=revision(a);assertEquals(403,call(a,HttpMethod.POST,"/api/bill",fields("accountId",cash,"categoryId",foreign,"type",1,"amount","1","billDate",today())).path("code").asInt());assertEquals(version,revision(a));String csv="type,categoryId,accountId,amount,billDate,remark\n1,1,"+cash+",1,"+today()+",valid\n1,1,"+second+",2,"+today()+",later invalid\n";Map<String,Object> preview=files.preview(a.id,new MockMultipartFile("file","test.csv","text/csv",csv.getBytes(StandardCharsets.UTF_8)));jdbc.update("UPDATE finance_account SET deleted=1 WHERE id=?",second);assertThrows(BusinessException.class,()->files.confirm(a.id,String.valueOf(preview.get("token"))));assertEquals(version,revision(a));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM bill WHERE user_id=?",Integer.class,a.id));money("0",summary(a).path("expense"));}
    @Test void paginationHasStableTieBreakAndRejectsUnboundedSizes(){Session a=session();long account=account(a,"Cash","CASH","0");for(int i=0;i<100;i++)jdbc.update("INSERT INTO bill(user_id,category_id,account_id,amount,type,bill_date,create_time) VALUES(?,1,?,1,1,?,'2026-01-01 12:00:00')",a.id,account,today());JsonNode first=call(a,HttpMethod.GET,"/api/bill/page?page=1&size=20",null).path("data"),second=call(a,HttpMethod.GET,"/api/bill/page?page=2&size=20",null).path("data");assertEquals(100,first.path("total").asInt());Set<Long> ids=new HashSet<>();for(JsonNode row:first.path("records"))ids.add(row.path("id").asLong());for(JsonNode row:second.path("records"))assertFalse(ids.contains(row.path("id").asLong()));assertTrue(first.path("records").get(19).path("id").asLong()>second.path("records").get(0).path("id").asLong());assertEquals(400,call(a,HttpMethod.GET,"/api/bill/page?size=101",null).path("code").asInt());}
    private long revision(Session s){return jdbc.queryForObject("SELECT revision FROM data_revision WHERE user_id=?",Long.class,s.id);}
    private JsonNode findAccount(JsonNode items,long id){for(JsonNode item:items)if(item.path("id").asLong()==id)return item;throw new AssertionError("Account absent");}
    private JsonNode accounts(Session s){JsonNode r=call(s,HttpMethod.GET,"/api/account/list",null);ok(r);return r.path("data");}
    private JsonNode assets(Session s){JsonNode r=call(s,HttpMethod.GET,"/api/assets/snapshot",null);ok(r);return r.path("data");}
    private JsonNode summary(Session s){JsonNode r=call(s,HttpMethod.GET,"/api/bill/statistics?startDate="+today().substring(0,7)+"-01&endDate="+today(),null);ok(r);return r.path("data");}
    private JsonNode categoryStats(Session s){JsonNode r=call(s,HttpMethod.GET,"/api/bill/category-stats?type=1&startDate="+today().substring(0,7)+"-01&endDate="+today(),null);ok(r);return r.path("data");}
    private Session session(){Session s=new Session();String name="p5_"+UUID.randomUUID().toString().substring(0,12);ok(call(null,HttpMethod.POST,"/api/user/register",fields("username",name,"password","Test123!")));JsonNode r=call(null,HttpMethod.POST,"/api/user/login",fields("username",name,"password","Test123!"));ok(r);s.id=r.path("data").path("userId").asLong();s.token=r.path("data").path("token").asText();return s;}
    private long account(Session s,String name,String type,String balance){ok(call(s,HttpMethod.POST,"/api/account",fields("name",name,"accountType",type,"openingBalance",balance,"openingDate","2020-01-01")));return jdbc.queryForObject("SELECT id FROM finance_account WHERE user_id=? AND name=?",Long.class,s.id,name);}
    private long bill(Session s,long account,long category,String amount){ok(call(s,HttpMethod.POST,"/api/bill",fields("accountId",account,"categoryId",category,"type",1,"amount",amount,"billDate",today())));return jdbc.queryForObject("SELECT MAX(id) FROM bill WHERE user_id=?",Long.class,s.id);}
    private String today(){return RecurringService.today().toString();}
    private JsonNode call(Session s,HttpMethod method,String path,Object body){HttpHeaders h=new HttpHeaders();h.setContentType(MediaType.APPLICATION_JSON);if(s!=null)h.setBearerAuth(s.token);return client.exchange(path,method,new HttpEntity<>(body,h),JsonNode.class).getBody();}
    private void ok(JsonNode r){assertNotNull(r);assertEquals(200,r.path("code").asInt());}
    private void money(String value,JsonNode n){assertTrue(n.isNumber());assertEquals(0,new BigDecimal(value).compareTo(n.decimalValue()));}
    private static Map<String,Object> fields(Object...p){Map<String,Object> r=new HashMap<>();for(int i=0;i<p.length;i+=2)r.put((String)p[i],p[i+1]);return r;}
    private static class Session{long id;String token;}
}
