package com.designart.observability;

import com.designart.support.SupportContext;
import com.designart.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.UUID;

/**
 * Captura CENTRALIZADA de erros de servidor. Fica na cadeia de segurança logo DEPOIS do filtro JWT: assim, ao
 * final da requisição, o TenantContext, o SecurityContext e o SupportContext ainda estão preenchidos (o filtro JWT
 * os limpa depois), o que permite associar o erro ao tenant correto e à Support Session, sem try/catch espalhado.
 *
 * <p>Só registra 5xx (exceção que escapa ao controlador ou resposta 5xx tratada). 4xx (validação, negócio,
 * autenticação, autorização) NÃO são incidentes. Nunca lê corpo, query, cabeçalhos ou mensagem de exceção. A
 * resposta original não é alterada: a exceção é relançada intacta.
 */
@Component
@RequiredArgsConstructor
public class ErrorCaptureFilter extends OncePerRequestFilter {

    private final ErrorRecorder recorder;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Throwable escaped = null;
        try {
            chain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException | Error e) {
            escaped = e;
            throw e;
        } finally {
            try {
                capture(request, response, escaped);
            } catch (RuntimeException ignored) {
                // Observabilidade nunca altera a resposta.
            }
        }
    }

    private void capture(HttpServletRequest request, HttpServletResponse response, Throwable escaped) {
        Throwable cause = escaped != null ? root(escaped) : (Throwable) request.getAttribute(DispatcherServlet.EXCEPTION_ATTRIBUTE);
        int status = escaped != null ? 500 : response.getStatus();
        if (status < 500) {
            return;
        }
        String correlation = request.getAttribute(CorrelationIdFilter.ATTRIBUTE) instanceof String s ? s : UUID.randomUUID().toString();
        String pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE) instanceof String p ? p : null;
        String path = ErrorClassifier.normalizePath(pattern, request.getRequestURI());
        SupportContext support = SupportContext.current();
        Long tenantId = support != null ? support.targetTenantId() : TenantContext.get();
        recorder.record(new ErrorRecorder.Signal(tenantId, support == null ? null : support.supportSessionId(), userId(),
                correlation, request.getMethod().toUpperCase(), path, status, ErrorClassifier.classify(cause, status)));
    }

    private static Throwable root(Throwable t) {
        Throwable c = t;
        for (int i = 0; i < 5 && c instanceof ServletException && c.getCause() != null; i++) {
            c = c.getCause();
        }
        return c;
    }

    private static Long userId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        try {
            return Long.valueOf(auth.getName());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
