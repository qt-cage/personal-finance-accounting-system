package com.finance.service;
import com.finance.common.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;
@Service
public class DictionaryService {
    private final JdbcTemplate jdbc;
    public DictionaryService(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public List<Map<String,Object>> list(){return jdbc.queryForList("SELECT id,kind,code,name,enabled,sort_order AS sortOrder FROM system_dictionary ORDER BY kind,sort_order,id");}
    public boolean disabled(String kind,String code){Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM system_dictionary WHERE kind=? AND code=? AND enabled=1",Integer.class,kind,code);return count==null||count==0;}
    public void update(Long id,String name,Boolean enabled){if(name==null||name.trim().isEmpty()||name.trim().length()>50||enabled==null)throw new BusinessException(400,"请填写50字以内的字典名称及状态");
        List<String> rows=jdbc.queryForList("SELECT CONCAT(kind,':',code) FROM system_dictionary WHERE id=?",String.class,id);if(rows.isEmpty())throw new BusinessException(404,"字典不存在");
        if("CURRENCY:CNY".equals(rows.get(0))&&!enabled)throw new BusinessException(409,"当前账本使用人民币，不能停用CNY");
        jdbc.update("UPDATE system_dictionary SET name=?,enabled=? WHERE id=?",name.trim(),enabled,id);
    }
}
