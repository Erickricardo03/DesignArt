package com.designart.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    // Hash BCrypt: nunca serializado em JSON nem impresso em toString/logs.
    @com.fasterxml.jackson.annotation.JsonIgnore
    @lombok.ToString.Exclude
    private String password;

    private String nomeCompleto;

    private String email;

    private String cargo;

    private String role; // ADMIN, COLABORADOR

    // Tenant ao qual este usuário pertence. NULLABLE de propósito: um futuro
    // SUPER_ADMIN da Nexus (Fase 4) não pertence a nenhum tenant específico —
    // ele administra a plataforma inteira. TENANT_ADMIN/USER sempre terão
    // tenant_id preenchido.
    @Column(name = "tenant_id")
    private Long tenantId;

    // Módulos de acesso liberados para este usuário além do que o role já garante
    // (ex: "FINANCEIRO", "EQUIPE", "CONFIGURACOES"). ADMIN sempre tem acesso total.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_permissoes", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "permissao")
    @Builder.Default
    private Set<String> permissoes = new HashSet<>();

    @Builder.Default
    private Boolean ativo = true;

    @Builder.Default
    private LocalDateTime dataCriacao = LocalDateTime.now();
}
