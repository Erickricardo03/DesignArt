package com.designart.observability;

import com.designart.exception.ServiceUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;

/** Regras puras de classificação, fingerprint, normalização de caminho e correlation ID. */
class ErrorClassifierTest {

    @Test
    void classificaPorTipo_eNuncaUsaAMensagem() {
        var db = ErrorClassifier.classify(new DataIntegrityViolationException("senha=abc token=xyz"), 500);
        assertThat(db.category()).isEqualTo(ErrorCategory.DATABASE);
        assertThat(ErrorClassifier.classify(new ServiceUnavailableException("x"), 503).category()).isEqualTo(ErrorCategory.INTEGRATION);
        assertThat(ErrorClassifier.classify(null, 503).category()).isEqualTo(ErrorCategory.INTEGRATION);
        var interno = ErrorClassifier.classify(new IllegalStateException("Bearer abc.def.ghi password=1"), 500);
        assertThat(interno.category()).isEqualTo(ErrorCategory.INTERNAL);
        assertThat(interno.safeMessage()).isEqualTo("Erro interno inesperado.");
        assertThat(interno.exceptionClass()).isEqualTo("java.lang.IllegalStateException");
        assertThat(db.safeMessage() + interno.safeMessage()).doesNotContain("senha").doesNotContain("Bearer");
        assertThat(ErrorClassifier.classify(null, 500).exceptionClass()).isNull();
    }

    @Test
    void fingerprintEEstavelEDependeSoDeCamposEstruturais() {
        String a = ErrorClassifier.fingerprint(ErrorCategory.INTERNAL, "INTERNAL_ERROR", "GET", "/api/x/{id}", "java.lang.IllegalStateException");
        assertThat(a).matches("^[0-9a-f]{64}$")
                .isEqualTo(ErrorClassifier.fingerprint(ErrorCategory.INTERNAL, "INTERNAL_ERROR", "GET", "/api/x/{id}", "java.lang.IllegalStateException"));
        assertThat(a).isNotEqualTo(ErrorClassifier.fingerprint(ErrorCategory.DATABASE, "INTERNAL_ERROR", "GET", "/api/x/{id}", "java.lang.IllegalStateException"));
        assertThat(a).isNotEqualTo(ErrorClassifier.fingerprint(ErrorCategory.INTERNAL, "INTERNAL_ERROR", "POST", "/api/x/{id}", "java.lang.IllegalStateException"));
        assertThat(a).isNotEqualTo(ErrorClassifier.fingerprint(ErrorCategory.INTERNAL, "INTERNAL_ERROR", "GET", "/api/y/{id}", "java.lang.IllegalStateException"));
        assertThat(a).isNotEqualTo(ErrorClassifier.fingerprint(ErrorCategory.INTERNAL, "INTERNAL_ERROR", "GET", "/api/x/{id}", "java.lang.RuntimeException"));
    }

    @Test
    void caminhoNormalizado_semQueryNemIds() {
        assertThat(ErrorClassifier.normalizePath("/api/tenants/{id}", "/api/tenants/77?token=abc")).isEqualTo("/api/tenants/{id}");
        assertThat(ErrorClassifier.normalizePath(null, "/api/tenants/77/users/550e8400-e29b-41d4-a716-446655440000?x=1"))
                .isEqualTo("/api/tenants/{id}/users/{id}");
        assertThat(ErrorClassifier.normalizePath(null, "/api/reset/" + "a".repeat(40))).isEqualTo("/api/reset/{id}");
        assertThat(ErrorClassifier.normalizePath(null, "/api/x y")).isEqualTo("/unknown");
        assertThat(ErrorClassifier.normalizePath(null, null)).isEqualTo("/unknown");
        assertThat(ErrorClassifier.normalizePath(null, "/" + "p".repeat(500)).length()).isLessThanOrEqualTo(200);
    }

    @Test
    void correlationIdAceitaSoFormatoEstrito() {
        assertThat(CorrelationIdFilter.resolve("abc-DEF_123.x")).isEqualTo("abc-DEF_123.x");
        assertThat(CorrelationIdFilter.resolve("1234567")).isNotEqualTo("1234567");
        assertThat(CorrelationIdFilter.resolve(null)).hasSize(36);
        assertThat(CorrelationIdFilter.resolve("bad id 12345")).hasSize(36);
        assertThat(CorrelationIdFilter.resolve("a".repeat(64))).hasSize(64);
        assertThat(CorrelationIdFilter.resolve("a".repeat(65))).hasSize(36);
    }
}
