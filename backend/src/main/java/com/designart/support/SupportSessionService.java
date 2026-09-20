package com.designart.support;

import com.designart.audit.*;
import com.designart.exception.ConflictException;
import com.designart.exception.ForbiddenOperationException;
import com.designart.exception.InvalidRequestException;
import com.designart.exception.ResourceNotFoundException;
import com.designart.model.Tenant;
import com.designart.repository.TenantRepository;
import com.designart.security.Role;
import com.designart.support.SupportDtos.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Ciclo de vida da Support Session (plano de controle, SUPER_ADMIN).
 *
 * <h3>Modelo de identidade</h3>
 * Ator = SUPER_ADMIN REAL (do JWT). Tenant = alvo. Nenhum JWT/usuário do cliente, nenhuma troca de tenant_id.
 * A sessão é referenciada por UUID e só é aceita quando o SUPER_ADMIN AUTENTICADO da requisição é o dono dela
 * (checado no banco a cada requisição): por isso não existe segredo de sessão a guardar ou vazar.
 *
 * <h3>Políticas</h3>
 * duração fixa de 30 min (sem renovação); no máximo UMA sessão ativa por SUPER_ADMIN (lock da linha do usuário +
 * índice único parcial); elevação para INTERVENTION exige motivo + confirmação e vale 10 min; sessão encerrada ou
 * expirada nunca volta a ser usada; tenant SUSPENSO pode receber suporte (a sessão não reativa o tenant).
 */
@Service
@RequiredArgsConstructor
public class SupportSessionService {

    private static final int MAX_PAGE_SIZE = 100;

    private final SupportSessionRepository sessions;
    private final TenantRepository tenants;
    private final AuditService auditService;
    private final AuditActors auditActors;
    private final com.designart.audit.ClientIpResolver ipResolver;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;

    // ------------------------------------------------------------------ início
    public SessionDto start(StartSessionRequest req) {
        if (req == null || req.tenantId() == null) {
            throw new InvalidRequestException("Informe a empresa (tenantId) e o motivo do suporte.");
        }
        String reason = SupportRules.reason(req.reason());
        AuditActor actor = superAdmin();
        Tenant tenant = tenants.findById(req.tenantId()).orElseThrow(() -> new ResourceNotFoundException("Tenant não encontrado."));
        HttpServletRequest http = currentRequest();
        String ip = ipResolver.resolveCurrent();
        String ua = http == null ? null : AuditSanitizer.userAgent(http.getHeader("User-Agent"));
        try {
            SupportSession saved = new TransactionTemplate(transactionManager).execute(status -> {
                LocalDateTime now = LocalDateTime.now(clock);
                sessions.lockSuperAdmin(actor.userId());          // serializa aberturas concorrentes do mesmo SUPER_ADMIN
                sessions.closeExpired(actor.userId(), now);       // sessões vencidas não bloqueiam uma nova
                if (sessions.findFirstBySuperAdminUserIdAndEndedAtIsNull(actor.userId()).isPresent()) {
                    throw new ConflictException("Já existe uma sessão de suporte ativa. Encerre-a antes de iniciar outra.");
                }
                SupportSession s = new SupportSession();
                s.setPublicId(UUID.randomUUID());
                s.setSuperAdminUserId(actor.userId());
                s.setTenantId(tenant.getId());
                s.setMode(SupportMode.READ_ONLY);
                s.setReason(reason);
                s.setStartedAt(now);
                s.setExpiresAt(now.plus(SupportSession.SESSION_TTL));
                s.setRequestIp(ip == null ? null : (ip.length() > 64 ? ip.substring(0, 64) : ip));
                s.setUserAgent(ua);
                s = sessions.saveAndFlush(s);
                auditService.success(AuditAction.SUPPORT_SESSION_STARTED, actor, target(s),
                        AuditMetadata.builder().supportMode(SupportMode.READ_ONLY).build());
                return s;
            });
            return toDto(saved, tenant.getName());
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Já existe uma sessão de suporte ativa. Encerre-a antes de iniciar outra.");
        }
    }

    // ------------------------------------------------------------------ encerramento (idempotente)
    @Transactional
    public SessionDto end(UUID publicId) {
        AuditActor actor = superAdmin();
        SupportSession s = lockOwned(publicId, actor);
        if (s.getEndedAt() == null) {
            s.setEndedAt(LocalDateTime.now(clock));
            s = sessions.saveAndFlush(s);
            auditService.success(AuditAction.SUPPORT_SESSION_ENDED, actor, target(s),
                    AuditMetadata.builder().supportMode(s.effectiveMode(LocalDateTime.now(clock))).build());
        }
        return toDto(s);
    }

    // ------------------------------------------------------------------ elevação
    @Transactional
    public SessionDto elevate(UUID publicId, ElevateRequest req) {
        AuditActor actor = superAdmin();
        SupportSession s = lockOwned(publicId, actor);
        LocalDateTime now = LocalDateTime.now(clock);
        if (!s.isActive(now)) {
            throw new ConflictException("A sessão de suporte está encerrada ou expirada.");
        }
        if (req == null || !Boolean.TRUE.equals(req.confirm())) {
            throw new InvalidRequestException("Confirme explicitamente a elevação para intervenção (confirm=true).");
        }
        String reason = SupportRules.reason(req.reason());
        if (s.effectiveMode(now) == SupportMode.INTERVENTION) {
            throw new ConflictException("A sessão já está em modo de intervenção.");
        }
        s.setMode(SupportMode.INTERVENTION);
        s.setElevatedAt(now);
        s.setElevationReason(reason);
        s = sessions.saveAndFlush(s);
        auditService.success(AuditAction.SUPPORT_SESSION_ELEVATED, actor, target(s),
                AuditMetadata.builder().supportMode(SupportMode.INTERVENTION).build());
        return toDto(s);
    }

    // ------------------------------------------------------------------ consultas
    @Transactional(readOnly = true)
    public SessionDto get(UUID publicId) {
        SupportSession s = sessions.findByPublicId(publicId).orElseThrow(() -> new ResourceNotFoundException("Sessão de suporte não encontrada."));
        if (!s.getSuperAdminUserId().equals(superAdmin().userId())) {
            throw new ForbiddenOperationException("Esta sessão de suporte pertence a outro administrador.");
        }
        return toDto(s);
    }

    /** Sessão ativa do SUPER_ADMIN autenticado (404 se não houver). */
    @Transactional(readOnly = true)
    public SessionDto current() {
        AuditActor actor = superAdmin();
        LocalDateTime now = LocalDateTime.now(clock);
        return sessions.findFirstBySuperAdminUserIdAndEndedAtIsNull(actor.userId())
                .filter(s -> s.isActive(now)).map(this::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Nenhuma sessão de suporte ativa."));
    }

    /** Histórico ("quem entrou em qual empresa, quando e por quê"). Sem o motivo da intervenção além do necessário. */
    @Transactional(readOnly = true)
    public Map<String, Object> list(Long tenantId, int page, int size) {
        Specification<SupportSession> spec = (root, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (tenantId != null) {
                p.add(cb.equal(root.get("tenantId"), tenantId));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
        Page<SupportSession> r = sessions.findAll(spec, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Order.desc("startedAt"), Sort.Order.desc("id"))));
        Map<Long, String> names = tenants.findAllById(r.getContent().stream().map(SupportSession::getTenantId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Tenant::getId, Tenant::getName));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", r.getContent().stream().map(s -> toDto(s, names.get(s.getTenantId()))).toList());
        out.put("page", r.getNumber());
        out.put("size", r.getSize());
        out.put("total", r.getTotalElements());
        return out;
    }

    // ------------------------------------------------------------------ autorização de operações
    /**
     * Valida uma operação da allowlist contra a sessão: dono = SUPER_ADMIN autenticado, sessão ativa, e modo efetivo
     * compatível (escrita exige INTERVENTION vigente). Em qualquer negação grava SUPPORT_ACTION_DENIED (transação
     * independente) e lança 403. Em sucesso publica o {@link SupportContext} da requisição (thread) e o devolve.
     */
    @Transactional(readOnly = true)
    public SupportContext authorize(UUID publicId, SupportOperation op) {
        AuditActor actor = superAdmin();
        SupportSession s = sessions.findByPublicId(publicId).orElseThrow(() -> new ResourceNotFoundException("Sessão de suporte não encontrada."));
        LocalDateTime now = LocalDateTime.now(clock);
        SupportMode effective = s.effectiveMode(now);
        String negativa = null;
        if (!s.getSuperAdminUserId().equals(actor.userId())) {
            negativa = "Esta sessão de suporte pertence a outro administrador.";
        } else if (!s.isActive(now)) {
            negativa = "A sessão de suporte está encerrada ou expirada.";
        } else if (op.isWrite() && effective != SupportMode.INTERVENTION) {
            negativa = "Esta operação exige elevação para modo de intervenção.";
        }
        if (negativa != null) {
            auditService.deniedIndependent(AuditAction.SUPPORT_ACTION_DENIED, actor, target(s),
                    AuditMetadata.builder().supportOperation(op).supportMode(effective).build());
            throw new ForbiddenOperationException(negativa);
        }
        SupportContext ctx = new SupportContext(s.getId(), s.getPublicId(), s.getSuperAdminUserId(), s.getTenantId(), effective);
        SupportContext.set(ctx);
        return ctx;
    }

    /** Registra a operação EXECUTADA (na transação do chamador, obrigatória). Sem payload, sem dados do cliente. */
    public void recordPerformed(SupportContext ctx, SupportOperation op) {
        auditService.success(AuditAction.SUPPORT_ACTION_PERFORMED, auditActors.current(),
                AuditTarget.entity(ctx.targetTenantId(), AuditEntityType.SUPPORT_SESSION, ctx.supportSessionId()),
                AuditMetadata.builder().supportOperation(op).supportMode(ctx.mode()).build());
    }

    /** Operação de suporte que FALHOU (transação independente). */
    public void recordFailed(SupportContext ctx, SupportOperation op) {
        auditService.failureIndependent(AuditAction.SUPPORT_ACTION_PERFORMED, auditActors.current(),
                AuditTarget.entity(ctx.targetTenantId(), AuditEntityType.SUPPORT_SESSION, ctx.supportSessionId()),
                AuditMetadata.builder().supportOperation(op).supportMode(ctx.mode()).build());
    }

    // ------------------------------------------------------------------ internos
    private AuditActor superAdmin() {
        AuditActor a = auditActors.current();
        if (a.userId() == null || a.role() != Role.SUPER_ADMIN) {
            throw new ForbiddenOperationException("Somente o SUPER_ADMIN pode usar o Modo Suporte.");
        }
        return a;
    }

    private SupportSession lockOwned(UUID publicId, AuditActor actor) {
        SupportSession s = sessions.findByPublicIdForUpdate(publicId).orElseThrow(() -> new ResourceNotFoundException("Sessão de suporte não encontrada."));
        if (!s.getSuperAdminUserId().equals(actor.userId())) {
            auditService.deniedIndependent(AuditAction.SUPPORT_ACTION_DENIED, actor, target(s),
                    AuditMetadata.builder().supportMode(s.effectiveMode(LocalDateTime.now(clock))).build());
            throw new ForbiddenOperationException("Esta sessão de suporte pertence a outro administrador.");
        }
        return s;
    }

    private static AuditTarget target(SupportSession s) {
        return AuditTarget.entity(s.getTenantId(), AuditEntityType.SUPPORT_SESSION, s.getId());
    }

    private static HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs ? attrs.getRequest() : null;
    }

    private SessionDto toDto(SupportSession s) {
        return toDto(s, tenants.findById(s.getTenantId()).map(Tenant::getName).orElse(null));
    }

    private SessionDto toDto(SupportSession s, String tenantName) {
        LocalDateTime now = LocalDateTime.now(clock);
        return new SessionDto(s.getPublicId(), s.getSuperAdminUserId(), s.getTenantId(), tenantName, s.effectiveMode(now),
                s.isActive(now), s.getReason(), s.getStartedAt(), s.getExpiresAt(), s.getElevatedAt(),
                s.elevationEndsAt(), s.getEndedAt());
    }
}
