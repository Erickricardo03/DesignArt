package com.designart.billing;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** ALLOW/DENY explícito de uma feature para UM tenant, acima do plano. Plano de controle (SUPER_ADMIN). */
@Entity
@Table(name = "tenant_feature_overrides", uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "feature_id"}))
@Getter
@Setter
@NoArgsConstructor
public class TenantFeatureOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @Column(name = "feature_id", nullable = false, updatable = false)
    private Long featureId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 5)
    private OverrideEffect effect;

    @Column(name = "limit_value")
    private Long limitValue;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by_user_id")
    private Long updatedByUserId;
}
