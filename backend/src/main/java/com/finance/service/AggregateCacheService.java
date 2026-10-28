package com.finance.service;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
@Service
public class AggregateCacheService {
    private final StringRedisTemplate redis;private final JdbcTemplate jdbc;private final ObjectMapper json;
    private final boolean enabled;private final String namespace;private final Duration ttl;
    private final AtomicLong hits=new AtomicLong(),misses=new AtomicLong(),fallbacks=new AtomicLong();
    public AggregateCacheService(StringRedisTemplate redis,JdbcTemplate jdbc,ObjectMapper json,@Value("${app.cache.enabled:false}")boolean enabled,@Value("${app.cache.namespace:finance:aggregate}")String namespace,@Value("${app.cache.ttl-seconds:60}")int seconds){
        if(!namespace.matches("[a-zA-Z0-9:_-]{1,100}"))throw new IllegalArgumentException("Cache namespace invalid");
        this.redis=redis;this.jdbc=jdbc;this.json=json.copy().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);this.enabled=enabled;this.namespace=namespace;this.ttl=Duration.ofSeconds(Math.max(10,Math.min(seconds,3600)));
    }
    public <T> T get(Long owner,String requestKey,TypeReference<T> type,Supplier<T> database){
        // 写事务中的状态可能回滚，不能写入不参与该事务的Redis。
        if(!enabled || (TransactionSynchronizationManager.isActualTransactionActive()
                && !TransactionSynchronizationManager.isCurrentTransactionReadOnly()))return database.get();
        String key=key(owner,requestKey);
        try{String value=redis.opsForValue().get(key);if(value!=null){T cached=json.readValue(value,type);hits.incrementAndGet();return cached;}}
        catch(Exception error){fallbacks.incrementAndGet();return database.get();}
        misses.incrementAndGet();T value=database.get();
        try{
            // 非事务读取可能跨过一次提交；新数据不能污染旧版本键及其他读事务的快照。
            if(key.equals(key(owner,requestKey)))redis.opsForValue().set(key,json.writeValueAsString(value),ttl);
        }catch(Exception error){fallbacks.incrementAndGet();}
        return value;
    }
    private String key(Long owner,String request){
        long own=0,shared=0;
        for(Map<String,Object> row:jdbc.queryForList("SELECT user_id,revision FROM data_revision WHERE user_id IN (?,0)",owner)){
            long id=((Number)row.get("user_id")).longValue(),version=((Number)row.get("revision")).longValue();if(id==0)shared=version;else if(id==owner.longValue())own=version;
        }
        try{byte[] hash=MessageDigest.getInstance("SHA-256").digest(request.getBytes(StandardCharsets.UTF_8));StringBuilder digest=new StringBuilder();for(byte b:hash)digest.append(String.format(Locale.ROOT,"%02x",b&255));return namespace+":"+owner+":"+own+":"+shared+":"+digest;}
        catch(Exception error){throw new IllegalStateException("Cache key unavailable",error);}
    }
    public Map<String,Long> metrics(){Map<String,Long> result=new LinkedHashMap<>();result.put("hits",hits.get());result.put("misses",misses.get());result.put("fallbacks",fallbacks.get());return result;}
}
