package com.finance.entity.dto;

import lombok.Data;
import javax.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class TransferDTO {
    @NotNull(message = "请选择转出账户") @Positive
    private Long accountId;
    @NotNull(message = "请选择转入账户") @Positive
    private Long transferAccountId;
    @NotNull(message = "转账金额不能为空")
    @DecimalMin(value = "0.01", message = "金额必须大于0")
    @DecimalMax(value = "9999999999.99", message = "金额超出允许范围")
    @Digits(integer = 10, fraction = 2, message = "金额最多保留两位小数")
    private BigDecimal amount;
    @NotNull(message = "日期不能为空")
    private LocalDate billDate;
    @Size(max = 200, message = "备注不能超过200个字符")
    private String remark;
    @NotBlank(message = "转账请求编号不能为空")
    @Pattern(regexp = "[A-Za-z0-9_-]{8,64}", message = "转账请求编号无效")
    private String requestId;
}
