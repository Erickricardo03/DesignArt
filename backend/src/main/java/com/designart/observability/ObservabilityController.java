package com.designart.observability;

import com.designart.admin.dto.AdminDtos.PageDto;
import com.designart.observability.ObservabilityDtos.*;
import com.designart.security.SuperAdminOnly;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/** Painel de erros por tenant (backend). Somente SUPER_ADMIN. Listagens paginadas (size máx. 100). */
@RestController
@RequestMapping("/api/admin")
@SuperAdminOnly
@RequiredArgsConstructor
public class ObservabilityController {

    private final ObservabilityService observability;

    @GetMapping("/observability/errors")
    public PageDto<ErrorEventDto> list(@RequestParam(required = false) Long tenantId,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                       @RequestParam(required = false) ErrorCategory category,
                                       @RequestParam(required = false) Integer status,
                                       @RequestParam(required = false) Boolean resolved,
                                       @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return observability.list(tenantId, from, to, category, status, resolved, page, size);
    }

    @GetMapping("/observability/errors/{id}")
    public ErrorEventDto get(@PathVariable Long id) {
        return observability.get(id);
    }

    @PostMapping("/observability/errors/{id}/resolve")
    public ErrorEventDto resolve(@PathVariable Long id) {
        return observability.resolve(id);
    }

    @PostMapping("/observability/errors/{id}/reopen")
    public ErrorEventDto reopen(@PathVariable Long id) {
        return observability.reopen(id);
    }

    @GetMapping("/tenants/{tenantId}/observability/summary")
    public TenantObservabilitySummaryDto summary(@PathVariable Long tenantId, @RequestParam(required = false) Integer days) {
        return observability.summary(tenantId, days);
    }
}
