package com.designart.model;

import com.designart.security.EmailAddress;
import com.designart.security.Permission;
import com.designart.security.Role;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Conta de acesso. Identidade de login = {@code email} (normalizado, único
 * globalmente). {@code username} é legado, nullable e NÃO é usado para login.
 * <p>
 * Regra estrutural: {@code SUPER_ADMIN <=> tenant_id IS NULL}. Vale no banco
 * (CHECK), aqui (callback de persistência) e no AccessPolicy.
 */
@Entity
@Table(name = "users")
@org.hibernate.annotations.Check(name = "users_role_tenant_chk",
        constraints = "(role = 'SUPER_ADMIN') = (tenant_id IS NULL)")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Legado (Fase 4.1): nullable, não usado para login, removido em etapa futura.
    @Column(unique = true)
    private String username;

    // Hash BCrypt: nunca serializado em JSON nem impresso em toString/logs.
    // Nullable: preparado para contas convidadas que ainda não definiram senha
    // (nunca autenticam enquanto for nulo).
    @Column
    @com.fasterxml.jackson.annotation.JsonIgnore
    @lombok.ToString.Exclude
    private String password;

    private String nomeCompleto;

    // Sempre normalizado (trim + lowercase/Locale.ROOT), obrigatório e único.
    @Column(nullable = false, unique = true)
    private String email;

    private String cargo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    // Tenant do usuário. NULL somente para SUPER_ADMIN (e obrigatório para os demais).
    @Column(name = "tenant_id")
    private Long tenantId;

    // Permissões de módulo do USER (TENANT_ADMIN as tem implicitamente; nada é gravado para ele).
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_permissoes", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "permissao", nullable = false)
    @Builder.Default
    private Set<Permission> permissoes = new HashSet<>();

    @Column(nullable = false)
    @Builder.Default
    private Boolean ativo = true;

    // Versão de sessão: o JWT carrega este valor (claim "tv"); incrementar invalida todos os JWT anteriores.
    @Column(name = "token_version", nullable = false)
    @Builder.Default
    private int tokenVersion = 0;

    private LocalDateTime emailVerifiedAt;

    @Column(name = "failed_logins", nullable = false)
    @Builder.Default
    private int failedLogins = 0;

    private LocalDateTime lockedUntil;

    private LocalDateTime lastLoginAt;

    @Builder.Default
    private LocalDateTime dataCriacao = LocalDateTime.now();

    /**
     * Convite ainda não aceito: sem senha e sem e-mail verificado. Estado coerente: {@code ativo=false},
     * {@code password=null}, {@code emailVerifiedAt=null}. Nunca autentica (senha nula) e não pode ser
     * ativado por um administrador — só o aceite do convite define a senha e ativa a conta.
     */
    public boolean isPendingInvite() {
        return password == null && emailVerifiedAt == null;
    }

    /** Invalida todos os JWT já emitidos para este usuário. */
    public void revokeSessions() {
        this.tokenVersion++;
    }

    @PrePersist
    @PreUpdate
    void validarInvariantes() {
        String normalizado = EmailAddress.normalizeOrNull(email);
        if (normalizado == null) {
            throw new IllegalStateException("Usuário exige um e-mail válido.");
        }
        this.email = normalizado;
        if (role == null) {
            throw new IllegalStateException("Usuário exige um role.");
        }
        if (role.belongsToTenant() && tenantId == null) {
            throw new IllegalStateException("Usuário com tenant_id nulo só pode ser SUPER_ADMIN.");
        }
        if (!role.belongsToTenant() && tenantId != null) {
            throw new IllegalStateException("SUPER_ADMIN não pode pertencer a um tenant.");
        }
        if (ativo == null) {
            throw new IllegalStateException("Usuário exige o campo ativo.");
        }
    }
}
