package com.designart.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "fotos_eventos")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FotoEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String codigoFoto; // ex: BR26-001

    private String titulo;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String urlOuBase64;

    @Builder.Default
    private BigDecimal preco = new BigDecimal("10.00");

    @Builder.Default
    private String marcaDaguaTexto = "PROIBIDA A CIRCULAÇÃO • DESIGN ARTE";

    @Builder.Default
    private Integer visualizacoes = 0;

    @Builder.Default
    private Integer vendas = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evento_id")
    @JsonBackReference
    private Evento evento;

    @Builder.Default
    private LocalDateTime dataUpload = LocalDateTime.now();
}
