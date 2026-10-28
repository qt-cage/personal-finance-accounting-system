package com.finance.service;
import com.finance.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service
public class AdminService {
    private final JdbcTemplate jdbc;
    public AdminService(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public Map<String,Object> users(String keyword,int page,int size){
        pagination(page,size);if(keyword==null)keyword="";if(keyword.length()>50)throw new BusinessException(400,"搜索内容最多50字");
        String where=" WHERE deleted=0 AND (username LIKE ? OR nickname LIKE ?)",match="%"+keyword+"%";
        Map<String,Object> result=new LinkedHashMap<>();result.put("total",jdbc.queryForObject("SELECT COUNT(*) FROM `user`"+where,Long.class,match,match));
        result.put("records",jdbc.queryForList("SELECT id,username,nickname,`role`,status,DATE_FORMAT(create_time,'%Y-%m-%d %H:%i:%s') AS createTime FROM `user`"+where+" ORDER BY id LIMIT ? OFFSET ?",match,match,size,(long)(page-1)*size));return result;
    }
    @Transactional
    public void updateUser(Long actor,Long id,String role,String status){
        if(!Arrays.asList("USER","ADMIN").contains(role)||!Arrays.asList("ACTIVE","DISABLED").contains(status))throw new BusinessException(400,"角色或状态无效");
        // 管理员集合按同一顺序锁定，防止两个并发撤权请求互相移除最后的管理入口。
        List<Long> admins=jdbc.queryForList("SELECT id FROM `user` WHERE deleted=0 AND `role`='ADMIN' AND status='ACTIVE' ORDER BY id FOR UPDATE",Long.class);
        if(!admins.contains(actor))throw new BusinessException(403,"当前管理员权限已失效");
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT `role`,status FROM `user` WHERE id=? AND deleted=0 FOR UPDATE",id);
        if(rows.isEmpty())throw new BusinessException(404,"用户不存在");
        if(actor.equals(id)&&(!"ADMIN".equals(role)||!"ACTIVE".equals(status)))throw new BusinessException(409,"不能停用或撤销自己的管理员权限");
        Map<String,Object> current=rows.get(0);
        if("ADMIN".equals(current.get("role"))&&"ACTIVE".equals(current.get("status"))&&(!"ADMIN".equals(role)||!"ACTIVE".equals(status))&&admins.size()<=1)throw new BusinessException(409,"必须保留一个启用的管理员");
        if(!role.equals(current.get("role"))||!status.equals(current.get("status")))jdbc.update("UPDATE `user` SET `role`=?,status=?,token_version=token_version+1 WHERE id=? AND deleted=0",role,status,id);
    }
    public Map<String,Object> logs(int page,int size){pagination(page,size);Map<String,Object> result=new LinkedHashMap<>();result.put("total",jdbc.queryForObject("SELECT COUNT(*) FROM operation_log",Long.class));result.put("records",jdbc.queryForList("SELECT l.id,l.actor_id AS actorId,u.username,l.method,l.route,l.outcome,DATE_FORMAT(l.create_time,'%Y-%m-%d %H:%i:%s') AS createTime FROM operation_log l LEFT JOIN `user` u ON u.id=l.actor_id ORDER BY l.id DESC LIMIT ? OFFSET ?",size,(long)(page-1)*size));return result;}
    private void pagination(int page,int size){if(page<1||size<1||size>100)throw new BusinessException(400,"分页参数无效，单页最多100条");}
}
