package com.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.finance.entity.Budget;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BudgetMapper extends BaseMapper<Budget> {
    @Insert("INSERT INTO budget(user_id, category_id, amount, month, deleted) "
            + "VALUES(#{b.userId}, #{b.categoryId}, #{b.amount}, #{b.month}, 0) "
            + "ON DUPLICATE KEY UPDATE amount = #{b.amount}, update_time = CURRENT_TIMESTAMP")
    int upsertActive(@Param("b") Budget budget);
}
