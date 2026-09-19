package com.designart.token;

/**
 * Token recém-emitido: o valor puro só existe aqui, em memória, até ser colocado no e-mail.
 * {@code toString} nunca imprime o token (evita vazamento acidental em logs/exceções).
 */
public record IssuedToken(Long id, String rawToken, ActionTokenPurpose purpose) {

    @Override
    public String toString() {
        return "IssuedToken[id=" + id + ", purpose=" + purpose + ", rawToken=[REDACTED]]";
    }
}
