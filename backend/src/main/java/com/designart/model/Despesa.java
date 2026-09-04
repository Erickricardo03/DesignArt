package com.designart.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "despesas")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Despesa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String descricao;

    @Column(nullable = false)
    private String categoria; // Equipamentos, Locação, Transporte, Alimentação, Equipe/Cachês, Software/Assinaturas, Outros

    @Column(nullable = false)
    private BigDecimal valor;

    @Column(nullable = false)
    private LocalDate dataDespesa;

    private String formaPagamento; // PIX, Cartão de Crédito, Boleto, Transferência

    @Column(nullable = false)
    @Builder.Default
    private String status = "PAGO"; // PAGO, PENDENTE

    private String observacoes;

    @Builder.Default
    private LocalDateTime dataRegistro = LocalDateTime.now();
}
