package com.designart.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "vendas_fotos", uniqueConstraints = @UniqueConstraint(name = "uk_vendas_fotos_tenant_codigo", columnNames = {"tenant_id", "codigo_venda"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VendaFoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Tenant dono deste registro. NUNCA aceito diretamente do cliente/DTO —
    // sempre atribuído pelo service a partir de TenantContext.require().
    @Column(name = "tenant_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Long tenantId;

    // Único POR TENANT, não globalmente — o código é gerado a partir do
    // timestamp (ver VendaService), então dois tenants podem gerar o mesmo
    // valor por coincidência; isso não pode fazer a venda de um tenant falhar
    // por causa de um código de outro tenant completamente não relacionado.
    private String codigoVenda; // ex: #257168767

    private String clienteNome;

    private String clienteEmail;

    private String eventoNome;

    @Builder.Default
    private Integer qtdFotos = 1;

    @Builder.Default
    private Integer qtdVideos = 0;

    @Column(nullable = false)
    private BigDecimal valorTotal;

    @Column(nullable = false)
    @Builder.Default
    private String status = "PAGO"; // PAGO, PENDENTE, ATRASADO

    private String dataDisponivelInfo; // ex: Disponível em 29/07/2026

    @Builder.Default
    private LocalDateTime dataVenda = LocalDateTime.now();
}
