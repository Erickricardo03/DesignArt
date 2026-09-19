package com.designart.security;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.*;

/** Exige a permissão CONFIGURACOES (TENANT_ADMIN a possui implicitamente; SUPER_ADMIN nunca). */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("hasAuthority('PERM_CONFIGURACOES')")
public @interface RequiresConfiguracoes {
}
