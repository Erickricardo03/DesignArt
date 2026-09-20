package com.designart.observability;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Plano de controle: só /api/admin/** e o suporte. */
public interface ApplicationErrorEventRepository extends JpaRepository<ApplicationErrorEvent, Long>, JpaSpecificationExecutor<ApplicationErrorEvent> {

    String BUMP = "update ApplicationErrorEvent e set e.occurrenceCount = e.occurrenceCount + 1, e.occurredAt = :now, "
            + "e.correlationId = :cid, e.userId = :uid, e.supportSessionId = :sid, e.httpStatus = :status "
            + "where e.fingerprint = :fp and e.resolvedAt is null and ";

    /** Incremento ATÔMICO do incidente aberto do tenant. Devolve 0 se não há incidente aberto. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(BUMP + "e.tenantId = :tenantId")
    int bumpOpenForTenant(Long tenantId, String fp, LocalDateTime now, String cid, Long uid, Long sid, int status);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(BUMP + "e.tenantId is null")
    int bumpOpenWithoutTenant(String fp, LocalDateTime now, String cid, Long uid, Long sid, int status);

    boolean existsByTenantIdAndFingerprintAndResolvedAtIsNull(Long tenantId, String fingerprint);

    boolean existsByTenantIdIsNullAndFingerprintAndResolvedAtIsNull(String fingerprint);

    Optional<ApplicationErrorEvent> findFirstByTenantIdOrderByOccurredAtDescIdDesc(Long tenantId);

    @Query("select count(e) from ApplicationErrorEvent e where e.tenantId = :tenantId and e.resolvedAt is null")
    long countUnresolved(Long tenantId);

    /** [categoria, incidentes, ocorrências] na janela. */
    @Query("select e.errorCategory, count(e), sum(e.occurrenceCount) from ApplicationErrorEvent e "
            + "where e.tenantId = :tenantId and e.occurredAt >= :from and e.occurredAt < :to group by e.errorCategory")
    List<Object[]> byCategory(Long tenantId, LocalDateTime from, LocalDateTime to);

    /** [método, caminho, incidentes, ocorrências] mais incidentes na janela (use Pageable para o top N). */
    @Query("select e.requestMethod, e.requestPath, count(e), sum(e.occurrenceCount) from ApplicationErrorEvent e "
            + "where e.tenantId = :tenantId and e.occurredAt >= :from and e.occurredAt < :to "
            + "group by e.requestMethod, e.requestPath order by sum(e.occurrenceCount) desc, e.requestPath asc")
    List<Object[]> topEndpoints(Long tenantId, LocalDateTime from, LocalDateTime to, Pageable page);
}
