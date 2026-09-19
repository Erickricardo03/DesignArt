package com.designart.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "logos_clientes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogoCliente {

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

    private String variante; // Principal, Negativa/Branca, Preta, Símbolo, Vetor

    private String formato; // PNG, SVG, JPG, EPS

    // TEXT (sem @Lob): portátil entre H2 e PostgreSQL. "LONGTEXT" é um tipo
    // MySQL que não existe no PostgreSQL — incompatibilidade real corrigida
    // na Fase 2. @Lob + columnDefinition juntos também forçariam o Hibernate
    // a tentar o tipo OID de large object do Postgres, que não é o que
    // queremos para um base64 armazenado como texto comum.
    @Column(columnDefinition = "TEXT")
    private String arquivoUrlOuBase64;

    private String tamanho;

    private String corPrimaria;

    @Builder.Default
    private LocalDateTime dataUpload = LocalDateTime.now();
}
