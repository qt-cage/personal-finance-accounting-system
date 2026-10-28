package com.finance.entity.dto;
import lombok.Data;
import javax.validation.constraints.*;
@Data
public class SystemSettingDTO {
    @NotBlank @Size(max=50) private String siteName;
    @NotNull private Boolean registrationEnabled;
}
