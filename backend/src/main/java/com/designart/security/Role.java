package com.designart.security;

/**
 * Nível ADMINISTRATIVO do usuário (não confundir com {@link Permission}, que é
 * capacidade de módulo dentro de um tenant).
 * <ul>
 *   <li>{@link #SUPER_ADMIN}: Nexus Development. Administra a plataforma e NÃO
 *       pertence a nenhum tenant (tenant_id NULL) — e só ele pode não pertencer.</li>
 *   <li>{@link #TENANT_ADMIN}: administrador de uma empresa cliente; tem todas as
 *       permissões do próprio tenant implicitamente.</li>
 *   <li>{@link #USER}: funcionário; só tem as permissões atribuídas explicitamente.</li>
 * </ul>
 * O valor nunca vem de uma string arbitrária: é enum no Java, CHECK no banco.
 */
public enum Role {
    SUPER_ADMIN,
    TENANT_ADMIN,
    USER;

    public String authority() {
        return "ROLE_" + name();
    }

    /** Roles que pertencem a um tenant (tenant_id obrigatório). */
    public boolean belongsToTenant() {
        return this != SUPER_ADMIN;
    }
}
