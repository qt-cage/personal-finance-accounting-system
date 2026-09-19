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
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** 仅在显式提供独立测试库时运行，禁止连接用户账本。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "FINANCE_INTEGRATION_DB_URL", matches = ".+")
class FinanceWorkflowIntegrationTest {
    @Autowired TestRestTemplate client;
    @Autowired JdbcTemplate jdbc;

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
    void assertIsolatedDatabase() {
        assertTrue(jdbc.queryForObject("SELECT DATABASE()", String.class).startsWith("finance_quality_test_"));
    }

    @Test
    void loginBillEditStatisticsAndAccountIsolation() {
        Account a = account();
        Account b = account();
        long category = category(a, "Daily", 1);
        Map<String, Object> bill = fields("categoryId", category, "type", 1, "amount", "0.30", "billDate", "2026-09-01", "remark", "before");
        success(call(a.token, HttpMethod.POST, "/api/bill", bill));
        long id = jdbc.queryForObject("SELECT id FROM bill WHERE user_id=? AND deleted=0", Long.class, a.id);
        bill.put("id", id);
        bill.put("remark", "after");
        success(call(a.token, HttpMethod.PUT, "/api/bill", bill));
        JsonNode rows = call(a.token, HttpMethod.GET, "/api/bill/page", null).path("data").path("records");
        assertEquals(category, rows.get(0).path("categoryId").asLong());
        assertEquals("after", rows.get(0).path("remark").asText());
        JsonNode totals = call(a.token, HttpMethod.GET, "/api/bill/statistics?startDate=2026-09-01&endDate=2026-09-30", null).path("data");
        assertEquals(0, totals.path("expense").decimalValue().compareTo(new java.math.BigDecimal("0.30")));
        assertEquals(403, call(b.token, HttpMethod.PUT, "/api/bill", bill).path("code").asInt());
        assertEquals(0, call(b.token, HttpMethod.GET, "/api/bill/page", null).path("data").path("total").asInt());
        assertEquals(409, call(a.token, HttpMethod.DELETE, "/api/category/" + category, null).path("code").asInt());
    }

    @Test
    void passwordChangeAndAccountDeletionInvalidateExistingTokens() {
        Account a = account();
        success(call(a.token, HttpMethod.PUT, "/api/user/password", fields("oldPassword", "Test123!", "newPassword", "Changed123!")));
        assertEquals(401, call(a.token, HttpMethod.GET, "/api/bill/page", null).path("code").asInt());
        JsonNode login = call(null, HttpMethod.POST, "/api/user/login", fields("username", a.name, "password", "Changed123!"));
        success(login);
        String nextToken = login.path("data").path("token").asText();
        // 更新普通资料不能把令牌版本重置。
        success(call(nextToken, HttpMethod.PUT, "/api/user/profile", fields("nickname", "Updated")));
        success(call(nextToken, HttpMethod.GET, "/api/user/info", null));
        assertEquals(401, call(a.token, HttpMethod.GET, "/api/bill/page", null).path("code").asInt());
        JsonNode oldAvatar = call(nextToken, HttpMethod.PUT, "/api/user/info?avatar=/uploads/avatars/foreign.png", null);
        assertNotEquals(200, oldAvatar.path("code").asInt());
        success(call(nextToken, HttpMethod.DELETE, "/api/user/account", null));
        assertEquals(401, call(nextToken, HttpMethod.GET, "/api/bill/page", null).path("code").asInt());
    }

    @Test
    void legacyOrMalformedClaimsCannotAccessBusinessEndpoints() {
        Account a = account();
        com.auth0.jwt.algorithms.Algorithm algorithm = com.auth0.jwt.algorithms.Algorithm.HMAC256(
                "integration-test-only-secret-at-least-32-characters");
        String legacyToken = com.auth0.jwt.JWT.create().withClaim("userId", a.id)
                .withExpiresAt(new Date(System.currentTimeMillis() + 60000)).sign(algorithm);
        assertEquals(401, call(legacyToken, HttpMethod.GET, "/api/bill/page", null).path("code").asInt());
        String malformedToken = com.auth0.jwt.JWT.create().withClaim("userId", "not-a-number")
                .withClaim("tokenVersion", 0).withExpiresAt(new Date(System.currentTimeMillis() + 60000)).sign(algorithm);
        assertEquals(401, call(malformedToken, HttpMethod.GET, "/api/bill/page", null).path("code").asInt());
    }

    @Test
    void categoryBudgetLifecycleAndRepeatedSameNameRecreation() {
        Account a = account();
        for (int cycle = 0; cycle < 3; cycle++) {
            long id = category(a, "Reusable", 1);
            assertEquals(409, call(a.token, HttpMethod.POST, "/api/category", fields("name", "Reusable", "type", 1)).path("code").asInt());
            assertEquals(400, call(a.token, HttpMethod.PUT, "/api/category", fields("id", id, "name", "Reusable", "type", 2)).path("code").asInt());
            success(call(a.token, HttpMethod.POST, "/api/budget", fields("categoryId", id, "month", "2026-09", "amount", "100")));
            assertEquals(409, call(a.token, HttpMethod.DELETE, "/api/category/" + id, null).path("code").asInt());
            long budget = jdbc.queryForObject("SELECT id FROM budget WHERE user_id=? AND category_id=? AND deleted=0", Long.class, a.id, id);
            success(call(a.token, HttpMethod.DELETE, "/api/budget/" + budget, null));
            success(call(a.token, HttpMethod.DELETE, "/api/category/" + id, null));
        }
    }

    @Test
    void concurrentBudgetSavesProduceOneActiveRowIncludingTotalBudget() throws Exception {
        Account a = account();
        long category = category(a, "Concurrent", 1);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            for (Long categoryId : Arrays.asList(null, category)) {
                CountDownLatch start = new CountDownLatch(1);
                List<Future<JsonNode>> calls = new ArrayList<>();
                for (int i = 0; i < 8; i++) {
                    final int amount = 100 + i;
                    calls.add(executor.submit(() -> {
                        start.await();
                        return call(a.token, HttpMethod.POST, "/api/budget", fields("categoryId", categoryId, "month", "2026-09", "amount", amount));
                    }));
                }
                start.countDown();
                for (Future<JsonNode> result : calls) success(result.get(30, TimeUnit.SECONDS));
                assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM budget WHERE user_id=? AND month='2026-09' AND category_key=? AND deleted=0",
                        Integer.class, a.id, categoryId == null ? 0L : categoryId));
            }
        } finally { executor.shutdownNow(); }
    }

    private Account account() {
        Account account = new Account();
        account.name = "qa_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        success(call(null, HttpMethod.POST, "/api/user/register", fields("username", account.name, "password", "Test123!")));
        JsonNode login = call(null, HttpMethod.POST, "/api/user/login", fields("username", account.name, "password", "Test123!"));
        success(login);
        account.token = login.path("data").path("token").asText();
        account.id = login.path("data").path("userId").asLong();
        return account;
    }

    private long category(Account account, String name, int type) {
        success(call(account.token, HttpMethod.POST, "/api/category", fields("name", name, "type", type)));
        return jdbc.queryForObject("SELECT id FROM category WHERE user_id=? AND name=? AND deleted=0", Long.class, account.id, name);
    }

    private JsonNode call(String token, HttpMethod method, String path, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("lang", "zh-cn");
        if (token != null) headers.setBearerAuth(token);
        JsonNode response = client.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class).getBody();
        assertNotNull(response);
        return response;
    }

    private static void success(JsonNode result) {
        assertEquals(200, result.path("code").asInt(), result.path("message").asText());
    }

    private static Map<String, Object> fields(Object... fields) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < fields.length; i += 2) map.put((String) fields[i], fields[i + 1]);
        return map;
    }

    private static class Account { String name; String token; long id; }
}
