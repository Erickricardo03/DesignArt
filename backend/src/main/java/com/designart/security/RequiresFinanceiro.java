package com.designart.security;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.*;

/** Exige a permissão FINANCEIRO (TENANT_ADMIN a possui implicitamente; SUPER_ADMIN nunca). */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("hasAuthority('PERM_FINANCEIRO')")
public @interface RequiresFinanceiro {
}
