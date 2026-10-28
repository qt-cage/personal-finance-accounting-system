package com.finance.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.finance.entity.Notification;
import org.apache.ibatis.annotations.*;
@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {
    @Select("SELECT IFNULL((SELECT enabled FROM notification_preference WHERE user_id=#{id}),1)")
    boolean enabled(@Param("id") Long id);
    @Insert("INSERT INTO notification_preference(user_id,enabled) VALUES(#{id},#{enabled}) ON DUPLICATE KEY UPDATE enabled=#{enabled}")
    void preference(@Param("id") Long id,@Param("enabled")boolean enabled);
    @Insert("INSERT IGNORE INTO notification(user_id,budget_id,month,stage,content) VALUES(#{n.userId},#{n.budgetId},#{n.month},#{n.stage},#{n.content})")
    void addOnce(@Param("n")Notification notification);
    @Update("UPDATE notification SET read_flag=1 WHERE user_id=#{id}")
    void readAll(@Param("id")Long id);
}
