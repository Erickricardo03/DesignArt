package com.designart.support;

/**
 * ALLOWLIST FECHADA das operações de suporte (fail-closed): o que não está aqui não existe para o Modo Suporte.
 * {@code write=true} exige sessão em INTERVENTION efetiva. Não há operações de billing (plano, assinatura,
 * cobrança, pagamento, inadimplência): essas ficam exclusivamente no Control Center administrativo normal.
 */
public enum SupportOperation {
    TENANT_OVERVIEW(false),
    TENANT_BRANDING(false),
    TENANT_ENTITLEMENTS(false),
    TENANT_USERS(false),
    TENANT_ERRORS(false),
    /** Reenviar convite pendente de um usuário DESTA empresa (única escrita habilitada inicialmente). */
    RESEND_INVITE(true);

    private final boolean write;

    SupportOperation(boolean write) {
        this.write = write;
    }

    public boolean isWrite() {
        return write;
    }
}
