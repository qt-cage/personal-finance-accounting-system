package com.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.finance.entity.Category;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CategoryMapper extends BaseMapper<Category> {
    // 新增账单/预算与删除分类使用同一行锁，避免检查后分类被并发删除。
    @Select("SELECT * FROM category WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    Category selectActiveForUpdate(@Param("id") Long id);
}
