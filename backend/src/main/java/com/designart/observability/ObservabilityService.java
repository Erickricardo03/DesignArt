package com.designart.observability;

import com.designart.admin.dto.AdminDtos.PageDto;
import com.designart.audit.AuditActors;
import com.designart.exception.ConflictException;
import com.designart.exception.InvalidRequestException;
import com.designart.exception.ResourceNotFoundException;
import com.designart.observability.ObservabilityDtos.*;
import com.designart.repository.TenantRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** Consulta e triagem dos incidentes (SUPER_ADMIN). Listagens paginadas; agregações em poucas consultas fixas. */
@Service
@RequiredArgsConstructor
public class ObservabilityService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_DAYS = 7;
    private static final int MAX_DAYS = 90;
    private static final int TOP_ENDPOINTS = 5;

    private final ApplicationErrorEventRepository repository;
    private final TenantRepository tenants;
    private final AuditActors auditActors;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageDto<ErrorEventDto> list(Long tenantId, LocalDate from, LocalDate to, ErrorCategory category,
                                       Integer httpStatus, Boolean resolved, int page, int size) {
        if (tenantId != null && !tenants.existsById(tenantId)) {
            throw new ResourceNotFoundException("Tenant não encontrado.");
        }
        if (from != null && to != null && !to.isAfter(from)) {
            throw new InvalidRequestException("Período inválido: informe from < to.");
        }
        Specification<ApplicationErrorEvent> spec = (root, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (tenantId != null) {
                p.add(cb.equal(root.get("tenantId"), tenantId));
            }
            if (from != null) {
                p.add(cb.greaterThanOrEqualTo(root.<LocalDateTime>get("occurredAt"), from.atStartOfDay()));
            }
            if (to != null) {
                p.add(cb.lessThan(root.<LocalDateTime>get("occurredAt"), to.atStartOfDay()));
            }
            if (category != null) {
                p.add(cb.equal(root.get("errorCategory"), category));
            }
            if (httpStatus != null) {
                p.add(cb.equal(root.get("httpStatus"), httpStatus));
            }
            if (resolved != null) {
                p.add(resolved ? cb.isNotNull(root.get("resolvedAt")) : cb.isNull(root.get("resolvedAt")));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
        Page<ApplicationErrorEvent> r = repository.findAll(spec, PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE), Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"))));
        return new PageDto<>(r.getContent().stream().map(ObservabilityService::toDto).toList(), r.getNumber(), r.getSize(), r.getTotalElements());
    }

    @Transactional(readOnly = true)
    public ErrorEventDto get(Long id) {
        return toDto(find(id));
    }

    /** Marca como resolvido (idempotente: já resolvido devolve o registro como está). */
    @Transactional
    public ErrorEventDto resolve(Long id) {
        ApplicationErrorEvent e = find(id);
        if (e.getResolvedAt() == null) {
            e.setResolvedAt(LocalDateTime.now(clock));
            e.setResolvedByUserId(auditActors.current().userId());
            e = repository.saveAndFlush(e);
        }
        return toDto(e);
    }

    /** Reabre (idempotente). Recusa se já existe outro incidente ABERTO com o mesmo fingerprint no mesmo tenant. */
    @Transactional
    public ErrorEventDto reopen(Long id) {
        ApplicationErrorEvent e = find(id);
        if (e.getResolvedAt() != null) {
            boolean jaAberto = e.getTenantId() == null
                    ? repository.existsByTenantIdIsNullAndFingerprintAndResolvedAtIsNull(e.getFingerprint())
                    : repository.existsByTenantIdAndFingerprintAndResolvedAtIsNull(e.getTenantId(), e.getFingerprint());
            if (jaAberto) {
                throw new ConflictException("Já existe um incidente aberto com o mesmo agrupamento.");
            }
            e.setResolvedAt(null);
            e.setResolvedByUserId(null);
            e = repository.saveAndFlush(e);
        }
        return toDto(e);
    }

    /** Resumo por tenant: 4 consultas fixas (sem N+1), independentes do volume. */
    @Transactional(readOnly = true)
    public TenantObservabilitySummaryDto summary(Long tenantId, Integer days) {
        if (tenantId == null || !tenants.existsById(tenantId)) {
            throw new ResourceNotFoundException("Tenant não encontrado.");
        }
        int window = days == null ? DEFAULT_DAYS : days;
        if (window < 1 || window > MAX_DAYS) {
            throw new InvalidRequestException("A janela deve estar entre 1 e " + MAX_DAYS + " dias.");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime start = now.minusDays(window);
        LocalDateTime end = now.plusSeconds(1);
        long incidents = 0;
        long occurrences = 0;
        Map<String, Long> byCategory = new TreeMap<>();
        for (Object[] r : repository.byCategory(tenantId, start, end)) {
            incidents += (Long) r[1];
            long occ = ((Number) r[2]).longValue();
            occurrences += occ;
            byCategory.put(((ErrorCategory) r[0]).name(), occ);
        }
        List<EndpointErrorDto> top = repository.topEndpoints(tenantId, start, end, PageRequest.of(0, TOP_ENDPOINTS)).stream()
                .map(r -> new EndpointErrorDto((String) r[0], (String) r[1], (Long) r[2], ((Number) r[3]).longValue())).toList();
        ErrorEventDto last = repository.findFirstByTenantIdOrderByOccurredAtDescIdDesc(tenantId).map(ObservabilityService::toDto).orElse(null);
        return new TenantObservabilitySummaryDto(tenantId, start.toLocalDate(), now.toLocalDate(), window, incidents, occurrences,
                repository.countUnresolved(tenantId), byCategory, top, last);
    }

    private ApplicationErrorEvent find(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Incidente não encontrado."));
    }

    static ErrorEventDto toDto(ApplicationErrorEvent e) {
        return new ErrorEventDto(e.getId(), e.getFirstOccurredAt(), e.getOccurredAt(), e.getTenantId(), e.getSupportSessionId(),
                e.getUserId(), e.getCorrelationId(), e.getRequestMethod(), e.getRequestPath(), e.getHttpStatus(), e.getErrorCode(),
                e.getErrorCategory(), e.getExceptionClass(), e.getSafeMessage(), e.getFingerprint(), e.getOccurrenceCount(),
                e.getResolvedAt() != null, e.getResolvedAt(), e.getResolvedByUserId());
    }
}
