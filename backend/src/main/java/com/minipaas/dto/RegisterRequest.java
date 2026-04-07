package com.minipaas.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    @Email(message = "Email không hợp lệ")
    @NotBlank
    private String email;

    @NotBlank
    @Size(min = 6, message = "Mật khẩu tối thiểu 6 ký tự")
    private String password;
}
