package com.finance.entity.dto;

import lombok.Data;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

@Data
public class CategoryReorderDTO {
    @NotNull @Positive private Long id;
    @NotNull @Positive private Long targetId;
}
