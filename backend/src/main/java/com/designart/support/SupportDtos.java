package com.designart.support;

import com.designart.admin.dto.AdminDtos.SubscriptionDto;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/** Contratos do Modo Suporte. Nenhum DTO carrega senha, hash, token, JWT, segredo nem dados financeiros. */
public final class SupportDtos {

    private SupportDtos() {
    }

    /** {@code tenantId} é o alvo (nunca tenant do próprio SUPER_ADMIN, que não tem). Motivo obrigatório. */
    public record StartSessionRequest(Long tenantId, String reason) {
    }

    /** Elevação explícita: motivo da intervenção + confirmação {@code true}. */
    public record ElevateRequest(String reason, Boolean confirm) {
    }

    /** {@code id} é o UUID da sessão (referência externa). {@code mode} = modo EFETIVO agora. */
    public record SessionDto(UUID id, Long superAdminUserId, Long tenantId, String tenantName, SupportMode mode,
                             boolean active, String reason, LocalDateTime startedAt, LocalDateTime expiresAt,
                             LocalDateTime elevatedAt, LocalDateTime elevationEndsAt, LocalDateTime endedAt) {
    }

    public record TenantOverviewDto(Long tenantId, String name, String slug, String status, String suspensionReason,
                                    LocalDateTime suspendedAt, String customDomain, SubscriptionDto subscription,
                                    long usersTotal, long usersActive, long usersPendingInvite) {
    }

    /** Usuário do tenant com e-mail MASCARADO (minimização de dados). */
    public record SupportUserDto(Long id, String emailMasked, String role, boolean active, boolean pendingInvite,
                                 Set<String> permissions) {
    }
}
