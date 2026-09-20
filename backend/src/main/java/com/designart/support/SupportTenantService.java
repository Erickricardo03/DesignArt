package com.designart.support;

import com.designart.admin.AdminBrandingService;
import com.designart.admin.AdminSubscriptionService;
import com.designart.admin.dto.AdminDtos.*;
import com.designart.exception.ResourceNotFoundException;
import com.designart.model.Tenant;
import com.designart.model.User;
import com.designart.observability.ObservabilityDtos.ErrorEventDto;
import com.designart.observability.ObservabilityService;
import com.designart.repository.TenantRepository;
import com.designart.repository.UserRepository;
import com.designart.service.InviteService;
import com.designart.support.SupportDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Operações de suporte habilitadas (a ALLOWLIST em código; cada método corresponde a uma {@link SupportOperation}).
 * Sempre recebem o {@link SupportContext} validado pelo portão e usam SOMENTE {@code ctx.targetTenantId()}:
 * nunca um tenantId vindo do cliente. Cada execução é auditada (SUPPORT_ACTION_PERFORMED) com o SUPER_ADMIN real,
 * o tenant alvo e a sessão. Não há acesso a billing (plano, assinatura, cobrança, pagamento): esses repositories e
 * serviços não são injetados aqui (guarda estrutural).
 */
@Service
@RequiredArgsConstructor
public class SupportTenantService {

    private final SupportSessionService sessions;
    private final TenantRepository tenantRepository;
    private final AdminSubscriptionService adminSubscriptions;
    private final AdminBrandingService adminBranding;
    private final ObservabilityService observability;
    private final UserRepository userRepository;
    private final InviteService inviteService;
    private final PlatformTransactionManager transactionManager;

    @Transactional
    public TenantOverviewDto overview(SupportContext ctx) {
        sessions.recordPerformed(ctx, SupportOperation.TENANT_OVERVIEW);
        Long tenantId = ctx.targetTenantId();
        Tenant t = tenantRepository.findById(tenantId).orElseThrow(() -> new ResourceNotFoundException("Tenant não encontrado."));
        SubscriptionDto sub = adminSubscriptions.findSubscription(tenantId).orElse(null);
        List<User> users = userRepository.findAllByTenantId(tenantId);
        long ativos = users.stream().filter(u -> Boolean.TRUE.equals(u.getAtivo())).count();
        long pendentes = users.stream().filter(User::isPendingInvite).count();
        return new TenantOverviewDto(t.getId(), t.getName(), t.getSlug(), t.getStatus(),
                t.getSuspensionReason() == null ? null : t.getSuspensionReason().name(), t.getSuspendedAt(),
                t.getCustomDomain(), sub, users.size(), ativos, pendentes);
    }

    @Transactional
    public BrandingDto branding(SupportContext ctx) {
        sessions.recordPerformed(ctx, SupportOperation.TENANT_BRANDING);
        return adminBranding.get(ctx.targetTenantId());
    }

    @Transactional
    public EntitlementsDto entitlements(SupportContext ctx) {
        sessions.recordPerformed(ctx, SupportOperation.TENANT_ENTITLEMENTS);
        return adminSubscriptions.entitlements(ctx.targetTenantId());
    }

    @Transactional
    public List<SupportUserDto> users(SupportContext ctx) {
        sessions.recordPerformed(ctx, SupportOperation.TENANT_USERS);
        return userRepository.findAllByTenantId(ctx.targetTenantId()).stream()
                .sorted(Comparator.comparing(User::getId))
                .map(u -> new SupportUserDto(u.getId(), SupportRules.maskEmail(u.getEmail()),
                        u.getRole() == null ? null : u.getRole().name(), Boolean.TRUE.equals(u.getAtivo()), u.isPendingInvite(),
                        u.getPermissoes() == null ? new TreeSet<String>()
                                : u.getPermissoes().stream().map(Enum::name).collect(Collectors.toCollection(TreeSet::new))))
                .toList();
    }

    @Transactional
    public PageDto<ErrorEventDto> errors(SupportContext ctx, int page, int size) {
        sessions.recordPerformed(ctx, SupportOperation.TENANT_ERRORS);
        return observability.list(ctx.targetTenantId(), null, null, null, null, null, page, size);
    }

    /** Escrita (INTERVENTION): reenvia o convite pendente de um usuário DESTE tenant. */
    public void resendInvite(SupportContext ctx, Long userId) {
        try {
            inviteService.resendFor(ctx.targetTenantId(), userId);
        } catch (RuntimeException e) {
            sessions.recordFailed(ctx, SupportOperation.RESEND_INVITE);
            throw e;
        }
        new TransactionTemplate(transactionManager).executeWithoutResult(s -> sessions.recordPerformed(ctx, SupportOperation.RESEND_INVITE));
    }
}
