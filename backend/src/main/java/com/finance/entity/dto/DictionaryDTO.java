package com.finance.entity.dto;
import lombok.Data;
import javax.validation.constraints.*;
@Data
public class DictionaryDTO {
    @NotBlank @Size(max=50) private String name;
    @NotNull private Boolean enabled;
}
