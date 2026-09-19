package com.designart.billing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlanRepository extends JpaRepository<Plan, Long> {
    Optional<Plan> findByCode(String code);

    boolean existsByCode(String code);
}
