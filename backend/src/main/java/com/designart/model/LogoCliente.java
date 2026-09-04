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

    @Column(nullable = false)
    private String clienteNome;

    private String variante; // Principal, Negativa/Branca, Preta, Símbolo, Vetor

    private String formato; // PNG, SVG, JPG, EPS

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String arquivoUrlOuBase64;

    private String tamanho;

    private String corPrimaria;

    @Builder.Default
    private LocalDateTime dataUpload = LocalDateTime.now();
}
