package com.designart.support;

import com.designart.admin.dto.AdminDtos.*;
import com.designart.observability.ObservabilityDtos.ErrorEventDto;
import com.designart.security.SuperAdminOnly;
import com.designart.support.SupportDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Modo Suporte (Support Session). Somente SUPER_ADMIN real. Duas áreas:
 * <ul>
 *   <li>ciclo de vida da sessão ({@code /sessions}, {@code /sessions/{id}/end|elevate});</li>
 *   <li>operações sobre o tenant ({@code /sessions/{id}/tenant/**}): SOMENTE handlers anotados com
 *       {@link SupportAllowed}; o {@link SupportAccessInterceptor} nega os demais e valida a sessão antes daqui.</li>
 * </ul>
 * Nenhuma rota recebe tenantId para operar: o alvo é o da sessão. Não é impersonação: o JWT continua sendo o do
 * SUPER_ADMIN e nada é executado "como" usuário do cliente.
 */
@RestController
@RequestMapping("/api/admin/support")
@SuperAdminOnly
@RequiredArgsConstructor
public class SupportController {

    private final SupportSessionService sessions;
    private final SupportTenantService tenant;

    // ---- ciclo de vida ----
    @PostMapping("/sessions")
    public ResponseEntity<SessionDto> start(@RequestBody StartSessionRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sessions.start(req));
    }

    @GetMapping("/sessions/current")
    public SessionDto current() {
        return sessions.current();
    }

    @GetMapping("/sessions")
    public Map<String, Object> list(@RequestParam(required = false) Long tenantId,
                                    @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return sessions.list(tenantId, page, size);
    }

    @GetMapping("/sessions/{id}")
    public SessionDto get(@PathVariable UUID id) {
        return sessions.get(id);
    }

    @PostMapping("/sessions/{id}/end")
    public SessionDto end(@PathVariable UUID id) {
        return sessions.end(id);
    }

    @PostMapping("/sessions/{id}/elevate")
    public SessionDto elevate(@PathVariable UUID id, @RequestBody ElevateRequest req) {
        return sessions.elevate(id, req);
    }

    // ---- allowlist: leitura ----
    @SupportAllowed(SupportOperation.TENANT_OVERVIEW)
    @GetMapping("/sessions/{id}/tenant/overview")
    public TenantOverviewDto overview(@PathVariable UUID id) {
        return tenant.overview(SupportContext.current());
    }

    @SupportAllowed(SupportOperation.TENANT_BRANDING)
    @GetMapping("/sessions/{id}/tenant/branding")
    public BrandingDto branding(@PathVariable UUID id) {
        return tenant.branding(SupportContext.current());
    }

    @SupportAllowed(SupportOperation.TENANT_ENTITLEMENTS)
    @GetMapping("/sessions/{id}/tenant/entitlements")
    public EntitlementsDto entitlements(@PathVariable UUID id) {
        return tenant.entitlements(SupportContext.current());
    }

    @SupportAllowed(SupportOperation.TENANT_USERS)
    @GetMapping("/sessions/{id}/tenant/users")
    public List<SupportUserDto> users(@PathVariable UUID id) {
        return tenant.users(SupportContext.current());
    }

    @SupportAllowed(SupportOperation.TENANT_ERRORS)
    @GetMapping("/sessions/{id}/tenant/errors")
    public PageDto<ErrorEventDto> errors(@PathVariable UUID id, @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "50") int size) {
        return tenant.errors(SupportContext.current(), page, size);
    }

    // ---- allowlist: escrita (exige INTERVENTION vigente) ----
    @SupportAllowed(SupportOperation.RESEND_INVITE)
    @PostMapping("/sessions/{id}/tenant/users/{userId}/resend-invite")
    public ResponseEntity<Void> resendInvite(@PathVariable UUID id, @PathVariable Long userId) {
        tenant.resendInvite(SupportContext.current(), userId);
        return ResponseEntity.noContent().build();
    }
}
