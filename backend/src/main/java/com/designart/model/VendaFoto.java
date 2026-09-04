package com.designart.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "vendas_fotos")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VendaFoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
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
