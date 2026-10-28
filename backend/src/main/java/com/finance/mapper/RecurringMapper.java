package com.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.finance.entity.RecurringBill;
import org.apache.ibatis.annotations.*;
import java.time.LocalDate;
import java.util.List;

@Mapper
public interface RecurringMapper extends BaseMapper<RecurringBill> {
    @Select("SELECT * FROM recurring_bill WHERE id=#{id} AND deleted=0 FOR UPDATE")
    RecurringBill lockActive(@Param("id") Long id);

    @Select("SELECT r.*, EXISTS(SELECT 1 FROM recurring_run h WHERE h.template_id=r.id) AS has_generated " +
            "FROM recurring_bill r WHERE r.user_id=#{userId} AND r.deleted=0 ORDER BY r.id DESC")
    List<RecurringBill> listForUser(@Param("userId") Long userId);

    @Select("SELECT r.id FROM recurring_bill r JOIN `user` u ON u.id=r.user_id AND u.deleted=0 AND u.status='ACTIVE' " +
            "WHERE r.deleted=0 AND r.enabled=1 AND r.next_date<=#{date} " +
            "AND (r.end_date IS NULL OR r.next_date<=r.end_date) " +
            "AND (#{userId} IS NULL OR r.user_id=#{userId}) " +
            "ORDER BY (r.last_error IS NOT NULL), r.update_time, r.id LIMIT 100")
    List<Long> dueIds(@Param("userId") Long userId, @Param("date") LocalDate date);

    @Select("SELECT COUNT(*) FROM recurring_run WHERE template_id=#{id}")
    long runCount(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM recurring_run WHERE template_id=#{id} AND due_date=#{date}")
    long occurrenceExists(@Param("id") Long id, @Param("date") LocalDate date);

    @Insert("INSERT INTO recurring_run(template_id,due_date,bill_id) VALUES(#{id},#{date},#{billId})")
    void recordRun(@Param("id") Long id, @Param("date") LocalDate date, @Param("billId") Long billId);

    // 重复相同错误也刷新尝试时间；下一轮优先处理尚未失败或更久未尝试的计划。
    @Update("UPDATE recurring_bill SET last_error=#{error}, update_time=CURRENT_TIMESTAMP WHERE id=#{id} AND deleted=0")
    void recordError(@Param("id") Long id, @Param("error") String error);
}
