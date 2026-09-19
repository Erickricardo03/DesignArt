package com.designart.security;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.*;

/** Somente a administração global da Nexus (endpoints /api/admin/**, previstos para etapas seguintes). */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("hasRole('SUPER_ADMIN')")
public @interface SuperAdminOnly {
}
