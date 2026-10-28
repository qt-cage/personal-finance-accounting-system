package com.finance.entity.dto;
import lombok.Data;
import javax.validation.constraints.*;
@Data
public class AdminUserDTO {
    @NotBlank @Pattern(regexp="USER|ADMIN") private String role;
    @NotBlank @Pattern(regexp="ACTIVE|DISABLED") private String status;
}
