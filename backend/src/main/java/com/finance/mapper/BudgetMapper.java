package com.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.finance.entity.Budget;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface BudgetMapper extends BaseMapper<Budget> {
    @Select("SELECT * FROM budget WHERE user_id=#{id} AND month=#{month} AND deleted=0 ORDER BY id FOR UPDATE")
    List<Budget> activeForUpdate(@Param("id")Long id,@Param("month")String month);
    @Insert("INSERT INTO budget(user_id, category_id, amount, month, deleted) "
            + "VALUES(#{b.userId}, #{b.categoryId}, #{b.amount}, #{b.month}, 0) "
            + "ON DUPLICATE KEY UPDATE amount = #{b.amount}, update_time = CURRENT_TIMESTAMP")
    int upsertActive(@Param("b") Budget budget);
}
