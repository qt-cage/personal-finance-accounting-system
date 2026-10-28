package com.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.finance.entity.Bill;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Mapper
public interface BillMapper extends BaseMapper<Bill> {

    @Select("SELECT IFNULL(SUM(CASE WHEN type=2 THEN amount ELSE 0 END),0) AS income, " +
            "IFNULL(SUM(CASE WHEN type=1 THEN amount ELSE 0 END),0) AS expense FROM bill " +
            "WHERE user_id=#{userId} AND deleted=0 AND type IN (1,2) AND bill_date BETWEEN #{start} AND #{end}")
    Map<String,BigDecimal> sumSummary(@Param("userId") Long userId, @Param("start") String start, @Param("end") String end);

    @Select("SELECT category_id, SUM(amount) AS total_amount FROM bill WHERE user_id=#{userId} " +
            "AND deleted=0 AND type=1 AND bill_date BETWEEN #{start} AND #{end} GROUP BY category_id")
    List<Map<String,Object>> expenseByCategory(@Param("userId") Long userId, @Param("start") String start, @Param("end") String end);

    @Select("SELECT * FROM bill WHERE id=#{id} AND deleted=0 FOR UPDATE")
    Bill selectActiveForUpdate(@Param("id") Long id);

    @Select("SELECT bill_date AS day, IFNULL(SUM(CASE WHEN type=1 THEN amount ELSE 0 END),0) AS expense, " +
            "IFNULL(SUM(CASE WHEN type=2 THEN amount ELSE 0 END),0) AS income FROM bill " +
            "WHERE user_id=#{userId} AND deleted=0 AND type IN (1,2) AND bill_date BETWEEN #{start} AND #{end} " +
            "GROUP BY bill_date ORDER BY bill_date")
    List<Map<String,Object>> sumByDayRange(@Param("userId") Long userId, @Param("start") String start, @Param("end") String end);

    @Select("SELECT COUNT(*) FROM bill WHERE user_id=#{userId} AND deleted=0 AND type IN (1,2) AND account_id IS NULL AND bill_date<=#{date}")
    long countUnassigned(@Param("userId") Long userId, @Param("date") String date);

    @Select("SELECT * FROM bill WHERE user_id=#{userId} AND request_id=#{requestId} FOR UPDATE")
    Bill selectTransferRequest(@Param("userId") Long userId, @Param("requestId") String requestId);

    @Select("SELECT IFNULL(SUM(amount), 0) FROM bill WHERE user_id = #{userId} AND type = #{type} AND bill_date BETWEEN #{startDate} AND #{endDate} AND deleted = 0")
    BigDecimal sumByTypeAndDateRange(Long userId, Integer type, String startDate, String endDate);

    @Select("SELECT IFNULL(SUM(amount), 0) FROM bill " +
            "WHERE user_id = #{userId} AND category_id IN (SELECT id FROM category " +
            "WHERE (id=#{categoryId} OR parent_id=#{categoryId}) AND deleted=0) AND type = #{type} " +
            "AND bill_date BETWEEN #{startDate} AND #{endDate} AND deleted = 0")
    BigDecimal sumByCategoryAndDateRange(@Param("userId") Long userId,
                                         @Param("categoryId") Long categoryId,
                                         @Param("type") Integer type,
                                         @Param("startDate") String startDate,
                                         @Param("endDate") String endDate);

    @Select("SELECT c.name AS category_name, IFNULL(SUM(b.amount), 0) AS total_amount " +
            "FROM bill b JOIN category c ON b.category_id = c.id " +
            "WHERE b.user_id = #{userId} AND b.type = #{type} AND b.bill_date BETWEEN #{startDate} AND #{endDate} AND b.deleted = 0 " +
            "GROUP BY b.category_id, c.name ORDER BY total_amount DESC")
    List<Map<String, Object>> sumByCategory(Long userId, Integer type, String startDate, String endDate);

    @Select("SELECT DATE_FORMAT(bill_date, '%Y-%m') AS month, IFNULL(SUM(amount), 0) AS total_amount " +
            "FROM bill WHERE user_id = #{userId} AND type = #{type} AND bill_date BETWEEN #{startDate} AND #{endDate} AND deleted = 0 " +
            "GROUP BY DATE_FORMAT(bill_date, '%Y-%m') ORDER BY month")
    List<Map<String, Object>> sumByMonth(Long userId, Integer type, String startDate, String endDate);

    /** 月度汇总报表：指定范围内的完整月度汇总 */
    @Select("SELECT DATE_FORMAT(bill_date, '%Y-%m') AS month, " +
            "IFNULL(SUM(CASE WHEN type=1 THEN amount ELSE 0 END), 0) AS expense, " +
            "IFNULL(SUM(CASE WHEN type=2 THEN amount ELSE 0 END), 0) AS income " +
            "FROM bill WHERE user_id = #{userId} AND type IN (1,2) AND bill_date BETWEEN #{startDate} AND #{endDate} AND deleted = 0 " +
            "GROUP BY DATE_FORMAT(bill_date, '%Y-%m') ORDER BY month")
    List<Map<String, Object>> sumByMonthRange(@Param("userId") Long userId,
                                               @Param("startDate") String startDate,
                                               @Param("endDate") String endDate);
}
