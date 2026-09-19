package com.designart.security;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.*;

/** Somente o administrador do próprio tenant. */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("hasRole('TENANT_ADMIN')")
public @interface TenantAdminOnly {
}
