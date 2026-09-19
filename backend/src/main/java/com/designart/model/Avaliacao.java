package com.designart.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "avaliacoes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Avaliacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Tenant dono deste registro. NUNCA aceito diretamente do cliente/DTO —
    // sempre atribuído pelo service a partir de TenantContext.require().
    @Column(name = "tenant_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Long tenantId;

    @Column(nullable = false)
    private String clienteNome;

    private String cargoEmpresa; // Ex: "CEO, Ateliê da Ysa"

    // TEXT (sem @Lob): já usava "TEXT" (válido no Postgres), mas @Lob por
    // padrão tentaria mapear como OID de large object — removido para
    // garantir mapeamento simples e portátil como texto comum.
    @Column(nullable = false, columnDefinition = "TEXT")
    private String texto;

    @Builder.Default
    private Integer nota = 5; // 1 a 5 estrelas

    @Builder.Default
    private Boolean ativo = true; // Controla se aparece publicamente na landing page

    @Builder.Default
    private LocalDateTime dataCriacao = LocalDateTime.now();
}
