package com.finance.entity.dto;

import lombok.Data;
import javax.validation.constraints.*;
import java.math.BigDecimal;

@Data
public class BudgetDTO {

    private Long categoryId;

    @NotNull(message = "预算金额不能为空")
    @DecimalMin(value = "0.01", message = "预算金额必须大于0")
    @DecimalMax(value = "9999999999.99", message = "预算金额超出允许范围")
    @Digits(integer = 10, fraction = 2, message = "预算金额最多保留两位小数")
    private BigDecimal amount;

    @NotBlank(message = "月份不能为空")
    @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])", message = "月份格式应为yyyy-MM")
    private String month;
}
