package com.designart.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "clientes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    private String segmento;

    private String contato;

    private String telefone;

    private String email;

    @Column(length = 1000)
    private String logoUrl;

    @Builder.Default
    private LocalDateTime dataCadastro = LocalDateTime.now();
}
