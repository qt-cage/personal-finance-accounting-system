package com.finance.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.finance.service.RecurringService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="app.recurring.enabled=false")
@EnabledIfEnvironmentVariable(named="FINANCE_INTEGRATION_DB_URL",matches=".+")
class RecurringAssetIntegrationTest {
    @Autowired TestRestTemplate client;
    @Autowired JdbcTemplate jdbc;
    @Autowired RecurringService recurring;
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        String url=System.getenv("FINANCE_INTEGRATION_DB_URL");
        if(url==null || !url.matches("jdbc:mysql://[^/]+/finance_quality_test_[a-zA-Z0-9_]+(?:\\?.*)?"))throw new IllegalArgumentException("Isolated database required");
        p.add("spring.datasource.url",()->url); p.add("spring.datasource.username",()->"root");
        p.add("spring.datasource.password",()->System.getenv("FINANCE_INTEGRATION_DB_PASSWORD"));
        p.add("jwt.secret",()->"integration-test-only-secret-at-least-32-characters");
        p.add("app.avatar.storage-dir",()->"./target/integration-avatars");
    }
    @BeforeEach void isolation() { assertTrue(jdbc.queryForObject("SELECT DATABASE()",String.class).startsWith("finance_quality_test_")); }

    @Test void eightConcurrentRunsGenerateOnceAndDeletionDoesNotRegenerate() throws Exception {
        Session s=session(); long account=account(s,"Cash","100","CASH"), category=category(s,1);
        ok(call(s,HttpMethod.POST,"/api/recurring",plan(account,category,"DAILY")));
        long id=jdbc.queryForObject("SELECT id FROM recurring_bill WHERE user_id=?",Long.class,s.id);
        ExecutorService pool=Executors.newFixedThreadPool(8);
        try {
            CountDownLatch start=new CountDownLatch(1); List<Future<Map<String,Integer>>> futures=new ArrayList<>();
            for(int i=0;i<8;i++)futures.add(pool.submit(()->{start.await();return recurring.runDue(s.id,RecurringService.today());}));
            start.countDown(); int generated=0;
            for(Future<Map<String,Integer>> f:futures){Map<String,Integer> r=f.get(30,TimeUnit.SECONDS);assertEquals(0,r.get("failed"));generated+=r.get("generated");}
            assertEquals(1,generated);
        }finally{pool.shutdownNow();}
        long bill=jdbc.queryForObject("SELECT bill_id FROM recurring_run WHERE template_id=?",Long.class,id);
        ok(call(s,HttpMethod.DELETE,"/api/bill/"+bill,null));
        jdbc.update("UPDATE recurring_bill SET next_date=? WHERE id=?",RecurringService.today().toString(),id);
        assertEquals(0,recurring.runDue(s.id,RecurringService.today()).get("generated"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM recurring_run WHERE template_id=?",Integer.class,id));
        assertEquals(1,jdbc.queryForObject("SELECT deleted FROM bill WHERE id=?",Integer.class,bill));
    }

    @Test void monthlyBacklogUsesClampedDatesThenReturnsToThirtyFirst() {
        Session s=session(); long account=account(s,"Cash","100","CASH"), category=category(s,1);
        jdbc.update("INSERT INTO recurring_bill(user_id,name,type,category_id,account_id,amount,frequency,start_date,end_date,next_date,enabled) VALUES(?,?,?,?,?,?,'MONTHLY','2026-01-31','2026-03-31','2026-01-31',1)",s.id,"Month end",1,category,account,new BigDecimal("10"));
        long id=jdbc.queryForObject("SELECT id FROM recurring_bill WHERE user_id=?",Long.class,s.id);
        assertEquals(3,recurring.runDue(s.id,LocalDate.of(2026,3,31)).get("generated"));
        assertEquals(Arrays.asList("2026-01-31","2026-02-28","2026-03-31"),jdbc.queryForList("SELECT CAST(due_date AS CHAR) FROM recurring_run WHERE template_id=? ORDER BY due_date",String.class,id));
        assertEquals("2026-04-30",jdbc.queryForObject("SELECT CAST(next_date AS CHAR) FROM recurring_bill WHERE id=?",String.class,id));
        assertEquals(0,recurring.runDue(s.id,LocalDate.of(2026,3,31)).get("generated"));
        money("70",assets(s).path("netAssets"));
    }

    @Test void pausedPlansProtectReferencesAndResumeSkipsMissedDates() {
        Session s=session(); long account=account(s,"Cash","100","CASH"), category=category(s,1);
        Map<String,Object> body=plan(account,category,"DAILY");ok(call(s,HttpMethod.POST,"/api/recurring",body));
        long id=jdbc.queryForObject("SELECT id FROM recurring_bill WHERE user_id=?",Long.class,s.id);
        ok(call(s,HttpMethod.PUT,"/api/recurring/"+id+"/status?enabled=false",null));
        assertEquals(0,recurring.runDue(s.id,RecurringService.today()).get("generated"));
        assertEquals(409,call(s,HttpMethod.DELETE,"/api/account/"+account,null).path("code").asInt());
        assertEquals(409,call(s,HttpMethod.DELETE,"/api/category/"+category,null).path("code").asInt());
        assertTrue(call(s,HttpMethod.GET,"/api/account/list",null).path("data").get(0).path("hasPlans").asBoolean());
        jdbc.update("UPDATE recurring_bill SET start_date=?,next_date=? WHERE id=?",RecurringService.today().minusDays(10).toString(),RecurringService.today().minusDays(10).toString(),id);
        ok(call(s,HttpMethod.PUT,"/api/recurring/"+id+"/status?enabled=true",null));
        assertEquals(RecurringService.today().toString(),jdbc.queryForObject("SELECT CAST(next_date AS CHAR) FROM recurring_bill WHERE id=?",String.class,id));
        assertEquals(1,recurring.runDue(s.id,RecurringService.today()).get("generated"));
    }

    @Test void foreignPlansAndAccountsAreInaccessibleAndFailedExecutionRollsBackCursor() {
        Session a=session(),b=session();long owned=account(a,"Mine","100","CASH"),foreign=account(b,"Other","100","CASH"),cat=category(a,1);
        assertEquals(403,call(a,HttpMethod.POST,"/api/recurring",plan(foreign,cat,"DAILY")).path("code").asInt());
        ok(call(a,HttpMethod.POST,"/api/recurring",plan(owned,cat,"DAILY")));
        long id=jdbc.queryForObject("SELECT id FROM recurring_bill WHERE user_id=?",Long.class,a.id);
        assertEquals(403,call(b,HttpMethod.DELETE,"/api/recurring/"+id,null).path("code").asInt());
        assertEquals(0,call(b,HttpMethod.GET,"/api/recurring/list",null).path("data").path("items").size());
        jdbc.update("UPDATE finance_account SET deleted=1 WHERE id=?",owned);
        assertEquals(1,recurring.runDue(a.id,RecurringService.today()).get("failed"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM bill WHERE user_id=?",Integer.class,a.id));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM recurring_run WHERE template_id=?",Integer.class,id));
        assertEquals(RecurringService.today().toString(),jdbc.queryForObject("SELECT CAST(next_date AS CHAR) FROM recurring_bill WHERE id=?",String.class,id));
        assertNotNull(jdbc.queryForObject("SELECT last_error FROM recurring_bill WHERE id=?",String.class,id));
        // 验证后停用失败样本，避免自动预览任务反复处理该故意破坏的测试记录。
        jdbc.update("UPDATE recurring_bill SET enabled=0 WHERE id=?",id);
    }

    @Test void assetDebtTransferAndUnassignedAmountsUseDifferentScopes() {
        Session s=session();long cash=account(s,"Cash","1000","CASH"),credit=account(s,"Card","-200","CREDIT");
        long expense=category(s,1),income=category(s,2);
        bill(s,cash,expense,1,"12.34",RecurringService.today());bill(s,cash,income,2,"25",RecurringService.today());
        ok(call(s,HttpMethod.POST,"/api/bill/transfer",fields("accountId",cash,"transferAccountId",credit,"amount","100","billDate",RecurringService.today().toString(),"requestId",UUID.randomUUID().toString())));
        bill(s,null,expense,1,"0.10",RecurringService.today());
        JsonNode a=assets(s); money("912.66",a.path("assets"));money("100",a.path("liabilities"));money("812.66",a.path("netAssets"));
        money("12.56",a.path("cashflowNet"));assertEquals(1,a.path("unassignedBillCount").asInt());assertEquals(30,a.path("dailyFlow").size());
        Session other=session();money("0",assets(other).path("netAssets"));assertEquals(0,assets(other).path("accounts").size());
    }

    @Test void thirtyDayBoundaryIncludesStartExcludesOlderAndFutureAndDailyTrendFillsGaps() {
        Session s=session();long cash=account(s,"Cash","0","CASH"),income=category(s,2),expense=category(s,1);
        LocalDate today=RecurringService.today();
        bill(s,cash,income,2,"1",today.minusDays(29));bill(s,cash,expense,1,"2",today.minusDays(30));bill(s,cash,income,2,"3",today.plusDays(1));
        JsonNode a=assets(s);money("1",a.path("cashflowIncome"));money("0",a.path("cashflowExpense"));money("-1",a.path("netAssets"));
        assertEquals(today.minusDays(29).toString(),a.path("dailyFlow").get(0).path("day").asText());
        money("1",a.path("dailyFlow").get(0).path("income"));
        JsonNode day=call(s,HttpMethod.GET,"/api/bill/day-stats?type=2&startDate="+today.minusDays(29)+"&endDate="+today.minusDays(28),null).path("data");
        assertEquals(2,day.size());money("1",day.get(0).path("total_amount"));money("0",day.get(1).path("total_amount"));
    }

    private Session session(){Session s=new Session();String name="p2_"+UUID.randomUUID().toString().replace("-","").substring(0,12);ok(call(null,HttpMethod.POST,"/api/user/register",fields("username",name,"password","Test123!")));JsonNode r=call(null,HttpMethod.POST,"/api/user/login",fields("username",name,"password","Test123!"));ok(r);s.id=r.path("data").path("userId").asLong();s.token=r.path("data").path("token").asText();return s;}
    private long account(Session s,String name,String value,String type){ok(call(s,HttpMethod.POST,"/api/account",fields("name",name,"accountType",type,"openingBalance",value,"openingDate","2020-01-01")));return jdbc.queryForObject("SELECT id FROM finance_account WHERE user_id=? AND name=?",Long.class,s.id,name);}
    private long category(Session s,int type){String name="Category "+type;ok(call(s,HttpMethod.POST,"/api/category",fields("name",name,"type",type)));return jdbc.queryForObject("SELECT id FROM category WHERE user_id=? AND name=?",Long.class,s.id,name);}
    private void bill(Session s,Long account,long category,int type,String value,LocalDate date){ok(call(s,HttpMethod.POST,"/api/bill",fields("accountId",account,"categoryId",category,"type",type,"amount",value,"billDate",date.toString())));}
    private Map<String,Object> plan(long account,long category,String frequency){return fields("name","Rent","type",1,"accountId",account,"categoryId",category,"amount","10","frequency",frequency,"startDate",RecurringService.today().toString());}
    private JsonNode assets(Session s){JsonNode r=call(s,HttpMethod.GET,"/api/assets/snapshot",null);ok(r);return r.path("data");}
    private JsonNode call(Session s,HttpMethod m,String path,Object b){HttpHeaders h=new HttpHeaders();h.setContentType(MediaType.APPLICATION_JSON);if(s!=null)h.setBearerAuth(s.token);return client.exchange(path,m,new HttpEntity<>(b,h),JsonNode.class).getBody();}
    private void ok(JsonNode r){assertNotNull(r);assertEquals(200,r.path("code").asInt(),r.toString());}
    private void money(String value,JsonNode n){assertTrue(n.isNumber(),n.toString());assertEquals(0,new BigDecimal(value).compareTo(n.decimalValue()),n.toString());}
    private static Map<String,Object> fields(Object...p){Map<String,Object> m=new HashMap<>();for(int i=0;i<p.length;i+=2)m.put((String)p[i],p[i+1]);return m;}
    private static class Session {long id;String token;}
}
