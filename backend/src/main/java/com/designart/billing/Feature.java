package com.designart.billing;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Funcionalidade comercializável (catálogo global). Não é uma permission de usuário. */
@Entity
@Table(name = "features")
@Getter
@Setter
@NoArgsConstructor
public class Feature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FeatureKind kind = FeatureKind.BOOLEAN;

    @Column(nullable = false)
    private boolean active = true;
}
