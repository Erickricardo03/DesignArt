package com.designart.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Redefinição de senha com token. Token e senhas nunca aparecem em toString/logs. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResetPasswordRequest {

    @NotBlank
    @Size(max = 200)
    @lombok.ToString.Exclude
    private String token;

    @NotBlank
    @Size(max = 400)
    @lombok.ToString.Exclude
    private String newPassword;

    @NotBlank
    @Size(max = 400)
    @lombok.ToString.Exclude
    private String confirmPassword;
}
