package com.minipaas.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class DeployRequest {

    /** URL GitHub của project cần deploy (phải là public repo) */
    @NotBlank(message = "GitHub URL không được để trống")
    @Pattern(
        regexp = "https://github\\.com/[\\w.-]+/[\\w.-]+",
        message = "Phải là GitHub URL hợp lệ (vd: https://github.com/user/repo)"
    )
    private String githubUrl;

    /** Branch cần build, mặc định 'main' */
    private String branch = "main";

    /** Port mà ứng dụng trong container lắng nghe */
    @Min(value = 1, message = "Port phải >= 1")
    @Max(value = 65535, message = "Port phải <= 65535")
    private int port = 8080;
}
