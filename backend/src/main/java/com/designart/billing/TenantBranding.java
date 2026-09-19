package com.designart.billing;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Identidade visual PERMITIDA de um tenant: somente cores hexadecimais e uma chave opaca de logo
 * (referência a object storage futuro; jamais binário, URL arbitrária, CSS ou HTML). O tenant vem
 * SEMPRE da identidade autenticada (ou do path administrativo do SUPER_ADMIN), nunca do corpo.
 */
@Entity
@Table(name = "tenant_branding")
@Getter
@Setter
@NoArgsConstructor
public class TenantBranding {

    @Id
    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "logo_ref", length = 255)
    private String logoRef;

    @Column(name = "primary_color", length = 7)
    private String primaryColor;

    @Column(name = "secondary_color", length = 7)
    private String secondaryColor;

    @Column(name = "accent_color", length = 7)
    private String accentColor;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by_user_id")
    private Long updatedByUserId;
}
