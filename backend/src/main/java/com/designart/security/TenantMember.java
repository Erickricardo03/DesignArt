package com.designart.security;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.*;

/** Endpoint tenant-scoped: TENANT_ADMIN ou USER. SUPER_ADMIN (sem tenant) e anônimos são negados. */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("hasAnyRole('TENANT_ADMIN','USER')")
public @interface TenantMember {
}
