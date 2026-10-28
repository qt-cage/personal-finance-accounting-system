package com.finance.service;
import com.finance.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service
public class SystemSettingsService {
    private final JdbcTemplate jdbc;
    public SystemSettingsService(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public Map<String,Object> read(){Map<String,Object> settings=new LinkedHashMap<>();settings.put("siteName",value("siteName","个人财务记账系统"));settings.put("registrationEnabled",!registrationClosed());return settings;}
    public boolean registrationClosed(){return "false".equals(value("registrationEnabled","true"));}
    private String value(String key,String fallback){List<String> rows=jdbc.queryForList("SELECT value FROM system_setting WHERE name=?",String.class,key);return rows.isEmpty()?fallback:rows.get(0);}
    @Transactional public void save(String name,Boolean enabled){if(name==null||name.trim().isEmpty()||name.trim().length()>50||enabled==null)throw new BusinessException(400,"请输入50字以内的系统名称及注册开关");put("siteName",name.trim());put("registrationEnabled",enabled.toString());}
    private void put(String name,String value){jdbc.update("INSERT INTO system_setting(name,value) VALUES(?,?) ON DUPLICATE KEY UPDATE value=VALUES(value)",name,value);}
}
