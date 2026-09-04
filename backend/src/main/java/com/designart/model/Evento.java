package com.designart.model;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "eventos")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Evento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome; // Ex: BARRA RUN 2026

    private String localizacao; // Ex: Barra de São Miguel - AL, Brasil

    private LocalDate dataEvento; // Ex: 01/08/2026

    private String horario; // Ex: 05:00 às 09:00

    @Builder.Default
    private BigDecimal precoFotoVendida = new BigDecimal("10.00");

    private Integer publicoEstimado; // Ex: 500

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String bannerUrl;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @Builder.Default
    private String status = "PUBLICADO"; // AGENDADO, EM_ANDAMENTO, PUBLICADO, FINALIZADO

    @OneToMany(mappedBy = "evento", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JsonManagedReference
    @Builder.Default
    private List<FotoEvento> fotos = new ArrayList<>();

    @Builder.Default
    private LocalDateTime dataCriacao = LocalDateTime.now();
}
