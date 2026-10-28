package com.finance.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AggregateCacheServiceTest {
    @Test void changedVersionAfterDatabaseReadDoesNotPopulateOldKey() {
        StringRedisTemplate redis=mock(StringRedisTemplate.class);JdbcTemplate jdbc=mock(JdbcTemplate.class);
        ValueOperations<String,String> values=mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(jdbc.queryForList(anyString(),eq(1L))).thenReturn(version(1),version(2));
        AggregateCacheService cache=new AggregateCacheService(redis,jdbc,new ObjectMapper(),true,"test",60);
        assertEquals("new",cache.get(1L,"accounts",new TypeReference<String>(){},()->"new"));
        verify(values,never()).set(anyString(),anyString(),any(Duration.class));
    }
    @Test void unchangedVersionCachesTheReadResult() {
        StringRedisTemplate redis=mock(StringRedisTemplate.class);JdbcTemplate jdbc=mock(JdbcTemplate.class);
        ValueOperations<String,String> values=mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(jdbc.queryForList(anyString(),eq(1L))).thenReturn(version(1));
        AggregateCacheService cache=new AggregateCacheService(redis,jdbc,new ObjectMapper(),true,"test",60);
        assertEquals("stable",cache.get(1L,"accounts",new TypeReference<String>(){},()->"stable"));
        verify(values).set(startsWith("test:1:1:0:"),eq("\"stable\""),eq(Duration.ofSeconds(60)));
    }
    @Test void writeTransactionNeverReadsOrPublishesCachedUncommittedState() {
        StringRedisTemplate redis=mock(StringRedisTemplate.class);JdbcTemplate jdbc=mock(JdbcTemplate.class);
        AggregateCacheService cache=new AggregateCacheService(redis,jdbc,new ObjectMapper(),true,"test",60);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
        try {
            assertEquals("uncommitted",cache.get(1L,"accounts",new TypeReference<String>(){},()->"uncommitted"));
            verifyNoInteractions(redis,jdbc);
        } finally {TransactionSynchronizationManager.clear();}
    }
    private List<Map<String,Object>> version(long number){Map<String,Object> row=new HashMap<>();row.put("user_id",1L);row.put("revision",number);return Collections.singletonList(row);}
}
