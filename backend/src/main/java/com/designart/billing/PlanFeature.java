package com.designart.billing;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Feature incluída (ou explicitamente desligada) em um plano, com limite numérico opcional. */
@Entity
@Table(name = "plan_features", uniqueConstraints = @UniqueConstraint(columnNames = {"plan_id", "feature_id"}))
@Getter
@Setter
@NoArgsConstructor
public class PlanFeature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_id", nullable = false, updatable = false)
    private Long planId;

    @Column(name = "feature_id", nullable = false, updatable = false)
    private Long featureId;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "limit_value")
    private Long limitValue;
}
