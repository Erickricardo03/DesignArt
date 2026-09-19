package com.designart.repository;

import com.designart.model.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, Long> {
    Optional<Tenant> findBySlug(String slug);
    Optional<Tenant> findByCustomDomain(String customDomain);
    boolean existsBySlug(String slug);

    /** Contagem por status em UMA consulta (dashboard). Cada linha: [status, quantidade]. */
    @org.springframework.data.jpa.repository.Query("select t.status, count(t) from Tenant t group by t.status")
    java.util.List<Object[]> countByStatusGrouped();

    @org.springframework.data.jpa.repository.Query("select count(t) from Tenant t where t.status = 'SUSPENSO' and t.suspensionReason = com.designart.model.SuspensionReason.NON_PAYMENT")
    long countSuspendedForNonPayment();
}
