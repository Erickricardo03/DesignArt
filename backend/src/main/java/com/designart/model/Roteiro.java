package com.designart.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "roteiros")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Roteiro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Tenant dono deste registro. NUNCA aceito diretamente do cliente/DTO —
    // sempre atribuído pelo service a partir de TenantContext.require().
    @Column(name = "tenant_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Long tenantId;

    @Column(nullable = false)
    private String titulo;

    @Column(nullable = false)
    private String loja; // Nome da Loja / Cliente

    private String criadorNome;

    private LocalDate dataGravacao;

    @Column(columnDefinition = "TEXT")
    private String conteudoScript;

    @Column(columnDefinition = "TEXT")
    private String observacoesSet;

    @Column(nullable = false)
    @Builder.Default
    private String status = "PENDENTE"; // PENDENTE, EM_GRAVACAO, CONCLUIDO

    @Builder.Default
    private Boolean feito = false;

    @Builder.Default
    private LocalDateTime dataCriacao = LocalDateTime.now();

    private LocalDateTime dataConclusao;
}
