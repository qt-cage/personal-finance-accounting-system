package com.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.finance.entity.Category;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CategoryMapper extends BaseMapper<Category> {
    // 新增账单/预算与删除分类使用同一行锁，避免检查后分类被并发删除。
    @Select("SELECT * FROM category WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    Category selectActiveForUpdate(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM recurring_bill WHERE category_id=#{id} AND deleted=0")
    long countPlanReferences(@Param("id") Long id);

    @Update("UPDATE category SET sort_order=#{sortOrder}, update_time=CURRENT_TIMESTAMP " +
            "WHERE id=#{id} AND user_id=#{userId} AND deleted=0")
    int updateOrder(@Param("id") Long id, @Param("userId") Long userId, @Param("sortOrder") int sortOrder);
}
