package com.designart.billing;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Plano de controle: só /api/admin/** e billing (ver ControlPlaneStructureGuardTest). */
public interface InvoiceRepository extends JpaRepository<Invoice, Long>, JpaSpecificationExecutor<Invoice> {

    Optional<Invoice> findBySubscriptionIdAndReferencePeriodAndStatusNot(Long subscriptionId, LocalDate period, InvoiceStatus status);

    /** Trava a linha (SELECT ... FOR UPDATE) para serializar pagamento/cancelamento concorrentes. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invoice i where i.id = :id")
    Optional<Invoice> findByIdForUpdate(Long id);

    List<Invoice> findTop12ByTenantIdOrderByReferencePeriodDescIdDesc(Long tenantId);

    /** OPEN -> OVERDUE em massa (idempotente). Devolve quantas linhas mudaram. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Invoice i set i.status = com.designart.billing.InvoiceStatus.OVERDUE, i.updatedAt = :now "
            + "where i.status = com.designart.billing.InvoiceStatus.OPEN and i.dueDate < :today")
    int markOverdue(LocalDate today, LocalDateTime now);

    /** Existe cobrança vencida (não paga) deste tenant? */
    @Query("select count(i) from Invoice i where i.tenantId = :tenantId and i.status = com.designart.billing.InvoiceStatus.OVERDUE")
    long countOverdue(Long tenantId);

    interface StatusTotal {
        InvoiceStatus getStatus();
        String getCurrency();
        long getQty();
        BigDecimal getTotal();
    }

    @Query("select i.status as status, i.currency as currency, count(i) as qty, sum(i.amount) as total "
            + "from Invoice i group by i.status, i.currency")
    List<StatusTotal> totalsByStatus();

    @Query("select i.status as status, i.currency as currency, count(i) as qty, sum(i.amount) as total "
            + "from Invoice i where i.tenantId = :tenantId group by i.status, i.currency")
    List<StatusTotal> totalsByStatusForTenant(Long tenantId);

    interface CurrencyTotal {
        String getCurrency();
        long getQty();
        BigDecimal getTotal();
    }

    @Query("select i.currency as currency, count(i) as qty, sum(i.amount) as total from Invoice i "
            + "where i.status = com.designart.billing.InvoiceStatus.OPEN and i.dueDate >= :today and i.dueDate <= :until "
            + "group by i.currency")
    List<CurrencyTotal> upcomingTotals(LocalDate today, LocalDate until);

    @Query("select i.currency as currency, count(i) as qty, sum(i.amount) as total from Invoice i "
            + "where i.status = com.designart.billing.InvoiceStatus.OVERDUE and i.graceEndsOn >= :today group by i.currency")
    List<CurrencyTotal> inGraceTotals(LocalDate today);

    @Query("select i.currency as currency, count(i) as qty, sum(i.amount) as total from Invoice i "
            + "where i.status = com.designart.billing.InvoiceStatus.OVERDUE and i.graceEndsOn < :today group by i.currency")
    List<CurrencyTotal> beyondGraceTotals(LocalDate today);

    @Query("select count(i) from Invoice i where i.tenantId = :tenantId and i.status = com.designart.billing.InvoiceStatus.OVERDUE "
            + "and i.graceEndsOn < :today")
    long countBeyondGraceForTenant(Long tenantId, LocalDate today);
}
