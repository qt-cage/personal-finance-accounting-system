package com.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.finance.entity.BillAttachment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AttachmentMapper extends BaseMapper<BillAttachment> {
    // 逻辑删除仍保留文件，不能靠删除记录状态重新获得物理空间额度。
    @Select("SELECT IFNULL(SUM(size),0) FROM bill_attachment WHERE user_id=#{userId}")
    long retainedBytes(@Param("userId") Long userId);
}
