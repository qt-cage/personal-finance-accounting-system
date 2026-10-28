package com.finance.entity.dto;

import lombok.Data;
import javax.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class AccountDTO {
    @NotBlank(message = "账户名称不能为空")
    @Size(max = 50, message = "账户名称不能超过50个字符")
    private String name;
    @NotBlank(message = "账户类型不能为空")
    @Pattern(regexp = "CASH|BANK|ALIPAY|WECHAT|CREDIT|INVESTMENT", message = "账户类型无效")
    private String accountType;
    @NotNull(message = "期初余额不能为空")
    @DecimalMin(value = "-9999999999.99", message = "期初余额超出范围")
    @DecimalMax(value = "9999999999.99", message = "期初余额超出范围")
    @Digits(integer = 10, fraction = 2, message = "金额最多保留两位小数")
    private BigDecimal openingBalance;
    @NotNull(message = "期初日期不能为空")
    private LocalDate openingDate;
}
