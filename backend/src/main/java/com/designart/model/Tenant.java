package com.designart.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Uma empresa cliente da Nexus Design (ex: DesignArte). Entidade ESTRUTURAL
 * do SaaS — não pertence a si mesma, é a unidade de isolamento que todas as
 * entidades TENANT-SCOPED referenciam via tenant_id.
 * <p>
 * A DesignArte NÃO é inserida como tenant nesta fase (ver Fase 5).
 */
@Entity
@Table(name = "tenants")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    @Builder.Default
    private String status = "ATIVO"; // ATIVO, INATIVO, SUSPENSO

    @Column(unique = true)
    private String customDomain;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt;
}
