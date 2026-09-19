package com.finance.entity.dto;

import lombok.Data;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Digits;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * 账单更新请求。仅暴露允许用户修改的字段，不接收 userId 等归属字段。
 */
@Data
public class BillUpdateDTO {

    @NotNull(message = "账单ID不能为空")
    private Long id;

    @NotNull(message = "分类不能为空")
    private Long categoryId;

    @NotNull(message = "金额不能为空")
    @DecimalMin(value = "0.01", message = "金额必须大于0")
    @DecimalMax(value = "9999999999.99", message = "金额超出允许范围")
    @Digits(integer = 10, fraction = 2, message = "金额最多保留两位小数")
    private BigDecimal amount;

    @NotNull(message = "类型不能为空")
    @Min(value = 1, message = "类型只能为支出或收入")
    @Max(value = 2, message = "类型只能为支出或收入")
    private Integer type;

    @Size(max = 200, message = "备注不能超过200个字符")
    private String remark;

    @NotBlank(message = "日期不能为空")
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "日期格式应为yyyy-MM-dd")
    private String billDate;
}
