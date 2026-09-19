package com.finance.entity.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * 分类新增请求。用户归属由服务端写入。
 */
@Data
public class CategoryCreateDTO {

    @NotBlank(message = "分类名称不能为空")
    @Size(max = 50, message = "分类名称不能超过50个字符")
    private String name;

    @NotNull(message = "分类类型不能为空")
    @Min(value = 1, message = "分类类型只能为支出或收入")
    @Max(value = 2, message = "分类类型只能为支出或收入")
    private Integer type;

    @Size(max = 50, message = "图标名称不能超过50个字符")
    private String icon;

    private Integer sortOrder;
}
