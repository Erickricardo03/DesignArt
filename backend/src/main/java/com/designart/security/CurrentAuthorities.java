package com.designart.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Consulta às authorities da requisição atual (construídas do banco por {@link AuthoritiesFactory}). */
public final class CurrentAuthorities {

    private CurrentAuthorities() {
    }

    public static boolean hasPermission(Permission permission) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> permission.authority().equals(a.getAuthority()));
    }
}
