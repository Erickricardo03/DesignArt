package com.designart.tenant;

import com.designart.exception.TenantContextException;

/**
 * Abstração central de resolução do tenant corrente da requisição.
 * <p>
 * Preenchido exclusivamente por {@code JwtAuthenticationFilter}, a partir do
 * {@code tenant_id} do usuário autenticado carregado do banco — NUNCA a
 * partir de um valor enviado pelo cliente (header, query param ou corpo da
 * requisição). Isso é o que impede um tenant de se passar por outro.
 * <p>
 * Todo service tenant-scoped deve consultar {@link #require()} em vez de
 * aceitar um tenantId como parâmetro vindo do controller/DTO.
 */
public final class TenantContext {

    private static final ThreadLocal<Long> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(Long tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static Long get() {
        return CURRENT_TENANT.get();
    }

    /**
     * Retorna o tenant corrente ou lança {@link TenantContextException} se
     * nenhum tenant estiver associado à requisição (ex: usuário ainda sem
     * tenant, como o admin de desenvolvimento da Fase 1 — cenário que só
     * será resolvido com o SUPER_ADMIN da Fase 4).
     */
    public static Long require() {
        Long tenantId = CURRENT_TENANT.get();
        if (tenantId == null) {
            throw new TenantContextException(
                    "Este usuário não está associado a nenhum tenant. " +
                    "Recursos multi-tenant ficarão disponíveis após a configuração do tenant do usuário.");
        }
        return tenantId;
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
