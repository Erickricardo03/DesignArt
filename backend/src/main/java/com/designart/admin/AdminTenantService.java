package com.designart.admin;

import com.designart.admin.dto.AdminDtos.*;
import com.designart.audit.*;
import com.designart.billing.*;
import com.designart.dto.UserDto;
import com.designart.dto.UsuarioRequest;
import com.designart.exception.ConflictException;
import com.designart.exception.InvalidRequestException;
import com.designart.exception.ResourceNotFoundException;
import com.designart.model.Tenant;
import com.designart.model.TenantStatus;
import com.designart.ratelimit.RateLimitPolicy;
import com.designart.ratelimit.RateLimitService;
import com.designart.repository.TenantRepository;
import com.designart.security.EmailAddress;
import com.designart.security.Role;
import com.designart.service.InviteService;
import com.designart.service.InviteService.ConvitePreparado;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Administração global de empresas (plano de controle, SUPER_ADMIN). Não usa TenantContext: o SUPER_ADMIN
 * não é usuário de nenhum tenant; o tenant alvo vem do path e é validado aqui.
 *
 * <h3>Criação de empresa (consistência transação × e-mail)</h3>
 * Uma transação ÚNICA e curta grava tenant + assinatura + TENANT_ADMIN pendente + token INVITE + auditoria
 * (tudo ou nada). O e-mail só é enviado DEPOIS do commit (assíncrono, fora da transação), pelo mesmo
 * mecanismo do convite da Fase 4.3; se o envio falhar, o token é revogado e auditado e o convite pode ser
 * reenviado. Sem SMTP habilitado nada é criado (503), para não existir empresa sem caminho de convite.
 * O SUPER_ADMIN nunca define nem vê a senha do cliente.
 */
@Service
@RequiredArgsConstructor
public class AdminTenantService {

    private final TenantRepository tenantRepository;
    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final InviteService inviteService;
    private final AuditService auditService;
    private final AuditActors auditActors;
    private final RateLimitService rateLimit;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<TenantDto> list(int page, int size) {
        int s = Math.min(Math.max(size, 1), 100);
        return tenantRepository.findAll(PageRequest.of(Math.max(page, 0), s, Sort.by("id"))).stream()
                .map(AdminTenantService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public TenantDto get(Long id) {
        return toDto(find(id));
    }

    public TenantCreatedDto create(CreateTenantRequest req) {
        if (req == null) {
            throw new InvalidRequestException("Corpo da requisição obrigatório.");
        }
        String name = AdminRules.name(req.name());
        String slug = AdminRules.slug(req.slug());
        String adminEmail = EmailAddress.normalizeOrNull(req.adminEmail());
        if (adminEmail == null) {
            throw new InvalidRequestException("Informe um e-mail válido para o administrador da empresa.");
        }
        String adminNome = AdminRules.optionalText(req.adminNome(), 120);
        Plan plan = planRepository.findByCode(AdminRules.code(req.planCode()))
                .filter(Plan::isActive)
                .orElseThrow(() -> new InvalidRequestException("Plano inexistente ou inativo."));
        LocalDateTime now = LocalDateTime.now(clock);
        if (req.currentPeriodEnd() != null && !req.currentPeriodEnd().isAfter(now)) {
            throw new InvalidRequestException("O fim do período deve ser futuro.");
        }

        inviteService.exigirEmailHabilitado();          // sem SMTP: 503 e NADA é criado
        AuditActor ator = auditActors.current();
        rateLimit.enforce(RateLimitPolicy.INVITE_CREATE_ACTOR, String.valueOf(ator.userId()));
        if (tenantRepository.existsBySlug(slug)) {
            throw new ConflictException("Já existe uma empresa com este identificador.");
        }

        Criado criado;
        try {
            criado = new TransactionTemplate(transactionManager).execute(status -> {
                Tenant tenant = new Tenant();
                tenant.setName(name);
                tenant.setSlug(slug);
                tenant.setStatus(TenantStatus.ATIVO.name());
                tenant.setCreatedAt(now);
                tenant.setUpdatedAt(now);
                tenant = tenantRepository.saveAndFlush(tenant);
                auditService.success(AuditAction.TENANT_CREATED, ator,
                        AuditTarget.entity(tenant.getId(), AuditEntityType.TENANT, tenant.getId()), AuditMetadata.EMPTY);

                Subscription sub = new Subscription();
                sub.setTenantId(tenant.getId());
                sub.setPlanId(plan.getId());
                sub.setStatus(SubscriptionStatus.ACTIVE);
                sub.setStartedAt(now);
                sub.setCurrentPeriodStart(now);
                sub.setCurrentPeriodEnd(req.currentPeriodEnd());
                sub.setCreatedAt(now);
                sub.setUpdatedAt(now);
                sub = subscriptionRepository.saveAndFlush(sub);
                auditService.success(AuditAction.SUBSCRIPTION_CREATED, ator,
                        AuditTarget.entity(tenant.getId(), AuditEntityType.SUBSCRIPTION, sub.getId()),
                        AuditMetadata.builder().subscriptionStatusChange(null, SubscriptionStatus.ACTIVE).planChanged(false).build());

                // Reutiliza o convite da Fase 4.3 (usuário PENDENTE sem senha + token + auditoria), papel fixo.
                UsuarioRequest convite = UsuarioRequest.builder()
                        .email(adminEmail).nomeCompleto(adminNome).role(Role.TENANT_ADMIN).build();
                ConvitePreparado c = inviteService.prepararConvite(tenant.getId(), convite, ator);
                return new Criado(tenant, sub, c);
            });
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Já existe uma empresa com este identificador.");
        }

        inviteService.despachar(criado.convite(), ator, criado.tenant().getName()); // fora da transação
        return new TenantCreatedDto(toDto(criado.tenant()), subscriptionDto(criado.subscription(), plan),
                UserDto.from(criado.convite().usuario()));
    }

    @Transactional
    public TenantDto suspend(Long id) {
        Tenant t = find(id);
        TenantStatus atual = TenantStatus.valueOf(t.getStatus());
        if (atual != TenantStatus.ATIVO) {
            throw new ConflictException("Somente uma empresa ativa pode ser suspensa.");
        }
        return mudarStatus(t, atual, TenantStatus.SUSPENSO, AuditAction.TENANT_SUSPENDED);
    }

    @Transactional
    public TenantDto reactivate(Long id) {
        Tenant t = find(id);
        TenantStatus atual = TenantStatus.valueOf(t.getStatus());
        if (atual == TenantStatus.ATIVO) {
            throw new ConflictException("A empresa já está ativa.");
        }
        return mudarStatus(t, atual, TenantStatus.ATIVO, AuditAction.TENANT_REACTIVATED);
    }

    /** Reenvia o convite pendente de um usuário DESTA empresa (reutiliza o mecanismo da Fase 4.3). */
    public UserDto resendInvite(Long tenantId, Long userId) {
        find(tenantId);
        return inviteService.resendFor(tenantId, userId);
    }

    /**
     * Altera SOMENTE Tenant.status (situação operacional). Não toca na assinatura, não apaga dados; o acesso
     * dos usuários é bloqueado porque AccessPolicy reavalia o tenant a cada requisição e no login.
     */
    private TenantDto mudarStatus(Tenant t, TenantStatus de, TenantStatus para, AuditAction acao) {
        t.setStatus(para.name());
        t.setUpdatedAt(LocalDateTime.now(clock));
        tenantRepository.saveAndFlush(t);
        auditService.success(acao, auditActors.current(),
                AuditTarget.entity(t.getId(), AuditEntityType.TENANT, t.getId()),
                AuditMetadata.builder().tenantStatusChange(de, para).build());
        return toDto(t);
    }

    private Tenant find(Long id) {
        return tenantRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Tenant não encontrado."));
    }

    private record Criado(Tenant tenant, Subscription subscription, ConvitePreparado convite) {
    }

    private static SubscriptionDto subscriptionDto(Subscription s, Plan plan) {
        return new SubscriptionDto(s.getId(), s.getTenantId(), plan.getCode(), s.getStatus(), s.getStartedAt(),
                s.getCurrentPeriodStart(), s.getCurrentPeriodEnd(), s.getGracePeriodEnd(), s.getCanceledAt());
    }

    static TenantDto toDto(Tenant t) {
        return new TenantDto(t.getId(), t.getName(), t.getSlug(), t.getStatus(), t.getCustomDomain(), t.getCreatedAt());
    }
}
