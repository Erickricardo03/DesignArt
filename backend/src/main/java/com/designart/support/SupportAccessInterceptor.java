package com.designart.support;

import com.designart.exception.ForbiddenOperationException;
import com.designart.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;
import java.util.UUID;

/**
 * PORTÃO FAIL-CLOSED das operações de suporte sobre o tenant ({@code /api/admin/support/sessions/{id}/tenant/**}).
 * Nenhum handler nessa árvore funciona sem estar EXPLICITAMENTE anotado com {@link SupportAllowed}: sem a anotação
 * a resposta é 403. Com ela, a sessão (dono, validade, modo efetivo x operação) é validada ANTES do controller, e o
 * {@link SupportContext} é publicado. O controller nunca recebe tenantId do cliente: o alvo vem da sessão.
 */
@Component
@RequiredArgsConstructor
public class SupportAccessInterceptor implements HandlerInterceptor {

    /** Padrão coberto pelo portão (usado também pelos testes estruturais). */
    public static final String PATH_PATTERN = "/api/admin/support/sessions/*/tenant/**";

    private final SupportSessionService sessions;

    @Override
    @SuppressWarnings("unchecked")
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod hm)) {
            throw new ForbiddenOperationException("Operação não habilitada para o Modo Suporte.");
        }
        SupportAllowed allowed = hm.getMethodAnnotation(SupportAllowed.class);
        if (allowed == null) {
            throw new ForbiddenOperationException("Operação não habilitada para o Modo Suporte.");
        }
        Map<String, String> vars = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        UUID id;
        try {
            id = UUID.fromString(vars == null ? "" : vars.getOrDefault("id", ""));
        } catch (IllegalArgumentException e) {
            throw new ResourceNotFoundException("Sessão de suporte não encontrada.");
        }
        sessions.authorize(id, allowed.value());
        return true;
    }
}
