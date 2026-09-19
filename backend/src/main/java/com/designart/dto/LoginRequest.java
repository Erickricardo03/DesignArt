package com.designart.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Contrato de login: e-mail + senha. O e-mail é normalizado (trim/lowercase) antes da consulta. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    @NotBlank
    private String email;

    @NotBlank
    @lombok.ToString.Exclude
    private String password;
}
