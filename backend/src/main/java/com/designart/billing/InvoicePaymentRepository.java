package com.designart.billing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Plano de controle: só /api/admin/** e billing. */
public interface InvoicePaymentRepository extends JpaRepository<InvoicePayment, Long>, JpaSpecificationExecutor<InvoicePayment> {

    List<InvoicePayment> findByInvoiceIdOrderByOccurredAtAscIdAsc(Long invoiceId);

    Optional<InvoicePayment> findFirstByTenantIdOrderByOccurredAtDescIdDesc(Long tenantId);

    boolean existsByInvoiceId(Long invoiceId);

    interface CurrencyTotal {
        String getCurrency();
        BigDecimal getTotal();
    }

    @Query("select p.currency as currency, sum(p.amount) as total from InvoicePayment p "
            + "where p.occurredAt >= :from and p.occurredAt < :to group by p.currency")
    List<CurrencyTotal> receivedBetween(LocalDateTime from, LocalDateTime to);
}
