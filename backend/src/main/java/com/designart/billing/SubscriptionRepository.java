package com.designart.billing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Plano de controle: sempre chamado com tenantId explícito vindo de path administrativo ou de TenantContext. */
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    Optional<Subscription> findByTenantId(Long tenantId);
}
