package com.finance.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** 新模型只允许在显式指定的独立测试库中验证。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "FINANCE_INTEGRATION_DB_URL", matches = ".+")
class AccountTransferIntegrationTest {
    @Autowired TestRestTemplate client;
    @Autowired JdbcTemplate jdbc;
    private static final String DATE = "2026-10-08";

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        String url = System.getenv("FINANCE_INTEGRATION_DB_URL");
        if (url == null || !url.matches("jdbc:mysql://[^/]+/finance_quality_test_[a-zA-Z0-9_]+(?:\\?.*)?")) {
            throw new IllegalArgumentException("Integration tests require a finance_quality_test_* database");
        }
        properties.add("spring.datasource.url", () -> url);
        properties.add("spring.datasource.username", () -> "root");
        properties.add("spring.datasource.password", () -> System.getenv("FINANCE_INTEGRATION_DB_PASSWORD"));
        properties.add("jwt.secret", () -> "integration-test-only-secret-at-least-32-characters");
        properties.add("app.avatar.storage-dir", () -> "./target/integration-avatars");
    }

    @BeforeEach
    void requireIsolatedDatabase() {
        assertTrue(jdbc.queryForObject("SELECT DATABASE()", String.class).startsWith("finance_quality_test_"));
    }

    @Test
    void transfersChangeBothBalancesWithoutChangingIncomeExpenseOrBudgets() {
        Session session = session();
        long source = account(session, "Cash", "1000.00");
        long target = account(session, "Bank", "50.00");
        long expenseCategory = category(session, "Dining", 1, null);
        long incomeCategory = category(session, "Salary", 2, null);
        bill(session, expenseCategory, source, 1, "12.34");
        bill(session, incomeCategory, source, 2, "25.00");
        ok(call(session, HttpMethod.POST, "/api/budget", fields("month", "2026-10", "amount", "100")));
        JsonNode totals = statistics(session);
        assertMoney("12.34", totals.path("expense"));
        assertMoney("25.00", totals.path("income"));
        Map<String, Object> payload = transfer(source, target, "100.00");
        JsonNode first = call(session, HttpMethod.POST, "/api/bill/transfer", payload);
        ok(first);
        long id = first.path("data").path("id").asLong();
        assertTrue(id > 0);
        ok(call(session, HttpMethod.POST, "/api/bill/transfer", payload));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM bill WHERE user_id=? AND type=3", Integer.class, session.id));
        assertMoney("912.66", balance(session, source));
        assertMoney("150.00", balance(session, target));
        assertEquals(totals, statistics(session));
        assertMoney("12.34", call(session, HttpMethod.GET, "/api/budget/list?month=2026-10", null).path("data").get(0).path("spentAmount"));
        JsonNode sourceBills = call(session, HttpMethod.GET, "/api/bill/page?accountId=" + source, null).path("data");
        JsonNode targetBills = call(session, HttpMethod.GET, "/api/bill/page?accountId=" + target, null).path("data");
        assertEquals(3, sourceBills.path("total").asInt());
        assertEquals(1, targetBills.path("total").asInt());
        assertEquals(source, targetBills.path("records").get(0).path("accountId").asLong());
        ok(call(session, HttpMethod.DELETE, "/api/bill/" + id, null));
        assertMoney("1012.66", balance(session, source));
        assertMoney("50.00", balance(session, target));
        assertEquals(totals, statistics(session));
        assertEquals(409, call(session, HttpMethod.POST, "/api/bill/transfer", payload).path("code").asInt());
    }

    @Test
    void concurrentRetriesProduceExactlyOneTransferAndRejectKeyReuseWithDifferentPayload() throws Exception {
        Session session = session();
        long source = account(session, "From", "1000.00");
        long target = account(session, "To", "0.00");
        Map<String, Object> body = transfer(source, target, "10.01");
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            CountDownLatch gate = new CountDownLatch(1);
            List<Future<JsonNode>> results = new ArrayList<>();
            for (int i = 0; i < 8; i++) results.add(executor.submit(() -> {
                gate.await();
                return call(session, HttpMethod.POST, "/api/bill/transfer", body);
            }));
            gate.countDown();
            Set<Long> ids = new HashSet<>();
            for (Future<JsonNode> result : results) {
                JsonNode response = result.get(30, TimeUnit.SECONDS);
                ok(response);
                ids.add(response.path("data").path("id").asLong());
            }
            assertEquals(1, ids.size());
        } finally { executor.shutdownNow(); }
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM bill WHERE user_id=?", Integer.class, session.id));
        assertMoney("989.99", balance(session, source));
        assertMoney("10.01", balance(session, target));
        body.put("amount", "12.00");
        assertEquals(409, call(session, HttpMethod.POST, "/api/bill/transfer", body).path("code").asInt());
    }

    @Test
    void crossUserAccountsCannotBeUsedReadUpdatedOrDeleted() {
        Session a = session();
        Session b = session();
        long owned = account(a, "Mine", "100");
        long foreign = account(b, "Private", "500");
        assertEquals(403, call(a, HttpMethod.POST, "/api/bill/transfer", transfer(owned, foreign, "10")).path("code").asInt());
        assertEquals(403, call(a, HttpMethod.DELETE, "/api/account/" + foreign, null).path("code").asInt());
        assertEquals(403, call(a, HttpMethod.PUT, "/api/account/" + foreign, accountBody("Other", "500")).path("code").asInt());
        assertEquals(1, call(a, HttpMethod.GET, "/api/account/list", null).path("data").size());
        assertEquals(0, call(a, HttpMethod.GET, "/api/bill/page?accountId=" + foreign, null).path("data").path("total").asInt());
        long category = category(a, "Food", 1, null);
        assertEquals(403, call(a, HttpMethod.POST, "/api/bill", fields("categoryId", category, "accountId", foreign,
                "type", 1, "amount", "1", "billDate", DATE)).path("code").asInt());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM bill WHERE user_id=?", Integer.class, a.id));
        assertMoney("100.00", balance(a, owned));
        assertMoney("500.00", balance(b, foreign));
    }

    @Test
    void openingBasisIsLockedAfterMovementButRenameRemainsPossible() {
        Session session = session();
        long account = account(session, "Cash", "20");
        long category = category(session, "Food", 1, null);
        bill(session, category, account, 1, "0.10");
        assertEquals(409, call(session, HttpMethod.PUT, "/api/account/" + account, accountBody("Cash", "30")).path("code").asInt());
        assertEquals(409, call(session, HttpMethod.DELETE, "/api/account/" + account, null).path("code").asInt());
        ok(call(session, HttpMethod.PUT, "/api/account/" + account, accountBody("Wallet", "20")));
        assertMoney("19.90", balance(session, account));
        assertEquals("Wallet", call(session, HttpMethod.GET, "/api/account/list", null).path("data").get(0).path("name").asText());
        assertEquals(409, call(session, HttpMethod.POST, "/api/account", accountBody("Wallet", "0")).path("code").asInt());
    }

    @Test
    void transferValidationRejectsSameAccountZeroMoneyBadDatesAndPreOpeningDates() {
        Session session = session();
        long source = account(session, "Cash", "10");
        long target = account(session, "Bank", "0");
        assertEquals(400, call(session, HttpMethod.POST, "/api/bill/transfer", transfer(source, source, "1")).path("code").asInt());
        assertEquals(400, call(session, HttpMethod.POST, "/api/bill/transfer", transfer(source, target, "0")).path("code").asInt());
        Map<String, Object> body = transfer(source, target, "1");
        body.put("billDate", "2019-12-31");
        assertEquals(400, call(session, HttpMethod.POST, "/api/bill/transfer", body).path("code").asInt());
        body.put("billDate", "not-a-date");
        assertEquals(400, call(session, HttpMethod.POST, "/api/bill/transfer", body).path("code").asInt());
        Map<String, Object> future = accountBody("Future", "0");
        future.put("openingDate", LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(1).toString());
        assertEquals(400, call(session, HttpMethod.POST, "/api/account", future).path("code").asInt());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM bill WHERE user_id=?", Integer.class, session.id));
    }

    @Test
    void parentBudgetIncludesOwnAndChildExpensesAndHierarchyRejectsInvalidRelationships() {
        Session session = session();
        Session foreign = session();
        long root = category(session, "Dining", 1, null);
        long child = category(session, "Lunch", 1, root);
        long otherRoot = category(session, "Travel", 1, null);
        long foreignRoot = category(foreign, "Private", 1, null);
        bill(session, root, null, 1, "2.20");
        bill(session, child, null, 1, "1.10");
        ok(call(session, HttpMethod.POST, "/api/budget", fields("categoryId", root, "month", "2026-10", "amount", "100")));
        ok(call(session, HttpMethod.POST, "/api/budget", fields("categoryId", child, "month", "2026-10", "amount", "50")));
        JsonNode budgets = call(session, HttpMethod.GET, "/api/budget/list?month=2026-10", null).path("data");
        for (JsonNode budget : budgets) assertMoney(budget.path("categoryId").asLong() == root ? "3.30" : "1.10", budget.path("spentAmount"));
        assertMoney("3.30", statistics(session).path("expense"));
        assertEquals(409, call(session, HttpMethod.DELETE, "/api/category/" + root, null).path("code").asInt());
        assertEquals(400, call(session, HttpMethod.POST, "/api/category", fields("name", "Third", "type", 1, "parentId", child)).path("code").asInt());
        assertEquals(400, call(session, HttpMethod.POST, "/api/category", fields("name", "Wrong type", "type", 2, "parentId", root)).path("code").asInt());
        assertEquals(403, call(session, HttpMethod.POST, "/api/category", fields("name", "Foreign", "type", 1, "parentId", foreignRoot)).path("code").asInt());
        assertEquals(400, call(session, HttpMethod.PUT, "/api/category", fields("id", root, "name", "Dining", "type", 1, "parentId", root)).path("code").asInt());
        assertEquals(409, call(session, HttpMethod.PUT, "/api/category", fields("id", root, "name", "Dining", "type", 1, "parentId", otherRoot)).path("code").asInt());
    }

    @Test
    void legacyCategoryUpdatePreservesParentAndExplicitNullPromotesIt() {
        Session session = session();
        long root = category(session, "Root", 1, null);
        long child = category(session, "Child", 1, root);
        ok(call(session, HttpMethod.PUT, "/api/category", fields("id", child, "name", "Renamed", "type", 1, "parentSpecified", true)));
        assertEquals(root, jdbc.queryForObject("SELECT parent_id FROM category WHERE id=?", Long.class, child));
        ok(call(session, HttpMethod.PUT, "/api/category", fields("id", child, "name", "Renamed", "type", 1, "parentId", null)));
        assertNull(jdbc.queryForObject("SELECT parent_id FROM category WHERE id=?", Long.class, child));
    }

    @Test
    void legacyBillUpdatesPreserveAssignedAccountAndCannotConvertTransfers() {
        Session session = session();
        long source = account(session, "Cash", "100");
        long target = account(session, "Bank", "0");
        long category = category(session, "Food", 1, null);
        bill(session, category, source, 1, "1.00");
        long id = jdbc.queryForObject("SELECT id FROM bill WHERE user_id=?", Long.class, session.id);
        ok(call(session, HttpMethod.PUT, "/api/bill", fields("id", id, "categoryId", category, "type", 1,
                "amount", "2.00", "billDate", DATE, "remark", "legacy update")));
        assertEquals(source, jdbc.queryForObject("SELECT account_id FROM bill WHERE id=?", Long.class, id));
        assertMoney("98.00", balance(session, source));
        JsonNode transfer = call(session, HttpMethod.POST, "/api/bill/transfer", transfer(source, target, "5"));
        ok(transfer);
        assertEquals(400, call(session, HttpMethod.PUT, "/api/bill", fields("id", transfer.path("data").path("id").asLong(),
                "categoryId", category, "type", 1, "amount", "5", "billDate", DATE)).path("code").asInt());
        assertMoney("93.00", balance(session, source));
        assertMoney("5.00", balance(session, target));
    }

    @Test
    void transferOnlyMonthDoesNotCreateARevenueRowAndBatchCannotPartiallyDelete() {
        Session a = session();
        Session b = session();
        long source = account(a, "Cash", "100");
        long target = account(a, "Bank", "0");
        JsonNode result = call(a, HttpMethod.POST, "/api/bill/transfer", transfer(source, target, "5"));
        ok(result);
        long transferId = result.path("data").path("id").asLong();
        assertEquals(0, call(a, HttpMethod.GET, "/api/bill/month-summary?startDate=2026-10-01&endDate=2026-10-31", null).path("data").size());
        long category = category(b, "Other", 1, null);
        bill(b, category, null, 1, "1");
        long foreignId = jdbc.queryForObject("SELECT id FROM bill WHERE user_id=?", Long.class, b.id);
        assertEquals(403, call(a, HttpMethod.DELETE, "/api/bill/batch", Arrays.asList(transferId, foreignId)).path("code").asInt());
        assertMoney("95.00", balance(a, source));
        assertMoney("5.00", balance(a, target));
        ok(call(a, HttpMethod.DELETE, "/api/bill/batch", Collections.singletonList(transferId)));
        assertMoney("100.00", balance(a, source));
        assertMoney("0.00", balance(a, target));
    }

    @Test
    void debtAndFutureOrDeletedMovementsHaveDefinedBalances() {
        Session session = session();
        Map<String, Object> body = accountBody("Credit", "-100.10");
        body.put("accountType", "CREDIT");
        ok(call(session, HttpMethod.POST, "/api/account", body));
        long account = jdbc.queryForObject("SELECT id FROM finance_account WHERE user_id=?", Long.class, session.id);
        long category = category(session, "Payment", 2, null);
        Map<String, Object> future = fields("accountId", account, "categoryId", category, "type", 2, "amount", "100",
                "billDate", LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(1).toString());
        ok(call(session, HttpMethod.POST, "/api/bill", future));
        assertMoney("-100.10", balance(session, account));
        bill(session, category, account, 2, "0.10");
        assertMoney("-100.00", balance(session, account));
        long id = jdbc.queryForObject("SELECT id FROM bill WHERE user_id=? AND amount=0.10", Long.class, session.id);
        ok(call(session, HttpMethod.DELETE, "/api/bill/" + id, null));
        assertMoney("-100.10", balance(session, account));
    }

    @Test
    void atomicCategoryReorderHandlesEqualPositionsAndRejectsForeignOrNonSiblingTargets() {
        Session s = session(), foreign = session();
        long a=category(s,"A",1,null), b=category(s,"B",1,null), c=category(s,"C",1,null);
        long child=category(s,"Child",1,a), other=category(foreign,"Foreign",1,null);
        ok(call(s,HttpMethod.POST,"/api/category/reorder",fields("id",a,"targetId",b)));
        assertEquals(Arrays.asList(b,a,c),jdbc.queryForList("SELECT id FROM category WHERE user_id=? AND parent_id IS NULL AND deleted=0 ORDER BY sort_order,id",Long.class,s.id));
        assertEquals(403,call(s,HttpMethod.POST,"/api/category/reorder",fields("id",a,"targetId",other)).path("code").asInt());
        assertEquals(400,call(s,HttpMethod.POST,"/api/category/reorder",fields("id",a,"targetId",child)).path("code").asInt());
        assertEquals(409,call(s,HttpMethod.POST,"/api/category/reorder",fields("id",b,"targetId",c)).path("code").asInt());
    }

    @Test
    void failedSecondCategoryWriteRollsBackBothOrderAndRevision() {
        Session s=session(); long a=category(s,"A",1,null),b=category(s,"B",1,null);
        long revision=jdbc.queryForObject("SELECT revision FROM data_revision WHERE user_id=?",Long.class,s.id);
        List<Integer> before=jdbc.queryForList("SELECT sort_order FROM category WHERE user_id=? ORDER BY id",Integer.class,s.id);
        jdbc.execute("CREATE TRIGGER finance_audit_reorder_failure BEFORE UPDATE ON category FOR EACH ROW BEGIN IF NEW.id="+a+" THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Synthetic second update failure'; END IF; END");
        try {
            assertNotEquals(200,call(s,HttpMethod.POST,"/api/category/reorder",fields("id",a,"targetId",b)).path("code").asInt());
            assertEquals(before,jdbc.queryForList("SELECT sort_order FROM category WHERE user_id=? ORDER BY id",Integer.class,s.id));
            assertEquals(revision,jdbc.queryForObject("SELECT revision FROM data_revision WHERE user_id=?",Long.class,s.id));
        } finally { jdbc.execute("DROP TRIGGER finance_audit_reorder_failure"); }
    }

    private Session session() {
        Session session = new Session();
        String name = "b1_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        ok(call(null, HttpMethod.POST, "/api/user/register", fields("username", name, "password", "Test123!")));
        JsonNode login = call(null, HttpMethod.POST, "/api/user/login", fields("username", name, "password", "Test123!"));
        ok(login);
        session.id = login.path("data").path("userId").asLong();
        session.token = login.path("data").path("token").asText();
        return session;
    }
    private Map<String, Object> accountBody(String name, String balance) {
        return fields("name", name, "accountType", "CASH", "openingBalance", balance, "openingDate", "2020-01-01");
    }
    private long account(Session session, String name, String balance) {
        ok(call(session, HttpMethod.POST, "/api/account", accountBody(name, balance)));
        return jdbc.queryForObject("SELECT id FROM finance_account WHERE user_id=? AND name=? AND deleted=0", Long.class, session.id, name);
    }
    private long category(Session session, String name, int type, Long parent) {
        ok(call(session, HttpMethod.POST, "/api/category", fields("name", name, "type", type, "parentId", parent)));
        return jdbc.queryForObject("SELECT id FROM category WHERE user_id=? AND name=? AND deleted=0", Long.class, session.id, name);
    }
    private void bill(Session session, long category, Long account, int type, String amount) {
        ok(call(session, HttpMethod.POST, "/api/bill", fields("categoryId", category, "accountId", account,
                "type", type, "amount", amount, "billDate", DATE)));
    }
    private Map<String, Object> transfer(long source, long target, String amount) {
        return fields("accountId", source, "transferAccountId", target, "amount", amount, "billDate", DATE,
                "remark", "test transfer", "requestId", UUID.randomUUID().toString());
    }
    private JsonNode balance(Session session, long id) {
        JsonNode accounts = call(session, HttpMethod.GET, "/api/account/list", null);
        ok(accounts);
        for (JsonNode account : accounts.path("data")) if (account.path("id").asLong() == id) return account.path("balance");
        fail("Account not returned");
        return null;
    }
    private JsonNode statistics(Session session) {
        return call(session, HttpMethod.GET, "/api/bill/statistics?startDate=2026-10-01&endDate=2026-10-31", null).path("data");
    }
    private JsonNode call(Session session, HttpMethod method, String path, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (session != null) headers.setBearerAuth(session.token);
        return client.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class).getBody();
    }
    private void ok(JsonNode result) { assertNotNull(result); assertEquals(200, result.path("code").asInt(), result.toString()); }
    private void assertMoney(String expected, JsonNode actual) { assertEquals(0, new BigDecimal(expected).compareTo(actual.decimalValue()), actual.toString()); }
    private static Map<String, Object> fields(Object... pairs) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) map.put((String) pairs[i], pairs[i + 1]);
        return map;
    }
    private static class Session { long id; String token; }
}
