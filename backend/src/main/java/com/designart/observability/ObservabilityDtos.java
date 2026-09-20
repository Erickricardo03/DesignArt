package com.designart.observability;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Contratos da observabilidade. Só metadados seguros: nenhum body, header, token, senha ou stack trace. */
public final class ObservabilityDtos {

    private ObservabilityDtos() {
    }

    public record ErrorEventDto(Long id, LocalDateTime firstOccurredAt, LocalDateTime occurredAt, Long tenantId,
                                Long supportSessionId, Long userId, String correlationId, String requestMethod,
                                String requestPath, int httpStatus, String errorCode, ErrorCategory errorCategory,
                                String exceptionClass, String safeMessage, String fingerprint, long occurrenceCount,
                                boolean resolved, LocalDateTime resolvedAt, Long resolvedByUserId) {
    }

    public record EndpointErrorDto(String method, String path, long incidents, long occurrences) {
    }

    /** {@code recentIncidents}/{@code recentOccurrences}: na janela. {@code unresolvedIncidents}: total em aberto. */
    public record TenantObservabilitySummaryDto(Long tenantId, LocalDate from, LocalDate to, int days,
                                                long recentIncidents, long recentOccurrences, long unresolvedIncidents,
                                                Map<String, Long> occurrencesByCategory,
                                                List<EndpointErrorDto> topEndpoints, ErrorEventDto lastError) {
    }

    public record HealthDto(String status, LocalDateTime timestamp, String application, String version,
                            Map<String, String> components) {
    }
}
