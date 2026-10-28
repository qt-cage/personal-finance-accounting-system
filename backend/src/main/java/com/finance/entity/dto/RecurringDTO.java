package com.finance.entity.dto;

import lombok.Data;
import javax.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class RecurringDTO {
    @NotBlank @Size(max = 50) private String name;
    @NotNull @Min(1) @Max(2) private Integer type;
    @NotNull @Positive private Long categoryId;
    @NotNull @Positive private Long accountId;
    @NotNull @DecimalMin("0.01") @DecimalMax("9999999999.99") @Digits(integer = 10, fraction = 2)
    private BigDecimal amount;
    @Size(max = 200) private String remark;
    @NotBlank @Pattern(regexp = "DAILY|WEEKLY|MONTHLY") private String frequency;
    @NotNull private LocalDate startDate;
    private LocalDate endDate;
}
