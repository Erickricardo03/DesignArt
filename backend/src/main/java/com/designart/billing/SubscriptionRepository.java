package com.designart.billing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Plano de controle: sempre chamado com tenantId explícito vindo de path administrativo ou de TenantContext. */
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    Optional<Subscription> findByTenantId(Long tenantId);

    /** SELECT ... FOR UPDATE: serializa a emissão de cobranças da mesma assinatura. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from Subscription s where s.id = :id")
    Optional<Subscription> findByIdForUpdate(Long id);

    /** Contagem por status comercial em UMA consulta (dashboard). Cada linha: [status, quantidade]. */
    @org.springframework.data.jpa.repository.Query("select s.status, count(s) from Subscription s group by s.status")
    java.util.List<Object[]> countByStatusGrouped();
}
