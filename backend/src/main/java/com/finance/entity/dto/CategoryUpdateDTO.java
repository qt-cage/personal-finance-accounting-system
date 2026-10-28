package com.finance.entity.dto;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.validation.constraints.Positive;

/**
 * 分类更新请求。归属用户由服务端认证上下文确定。
 */
@Data
public class CategoryUpdateDTO {

    @NotNull(message = "分类ID不能为空")
    private Long id;

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

    @Positive(message = "父分类ID无效")
    private Long parentId;

    @JsonIgnore
    private boolean parentSpecified;

    /** 兼容未传parentId的旧客户端，同时允许明确传null提升为一级分类。 */
    @JsonSetter("parentId")
    public void setParentId(Long parentId) {
        this.parentId = parentId;
        this.parentSpecified = true;
    }
}
