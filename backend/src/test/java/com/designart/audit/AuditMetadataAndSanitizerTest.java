package com.designart.audit;

import com.designart.security.Permission;
import com.designart.security.Role;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Metadata tipado (sem texto livre) e saneamento de User-Agent. */
class AuditMetadataAndSanitizerTest {

    // ---------------- metadata ----------------

    @Test
    void metadataVazioViraNull() {
        assertThat(AuditMetadata.EMPTY.toJson()).isNull();
        assertThat(AuditMetadata.builder().build().toJson()).isNull();
    }

    @Test
    void jsonEhDeterministicoEUsaApenasNomesDeEnum() {
        String json = AuditMetadata.builder()
                .roleChange(Role.USER, Role.TENANT_ADMIN)
                .permissionsAdded(EnumSet.of(Permission.FINANCEIRO, Permission.EQUIPE))
                .permissionsRemoved(EnumSet.noneOf(Permission.class))
                .activeChange(true, false)
                .fieldsChanged(EnumSet.of(AuditField.CARGO, AuditField.EMAIL))
                .reasons(EnumSet.of(AuditReason.ROLE_CHANGED))
                .build().toJson();
        assertThat(json).isEqualTo("{\"roleFrom\":\"USER\",\"roleTo\":\"TENANT_ADMIN\","
                + "\"permissionsAdded\":[\"EQUIPE\",\"FINANCEIRO\"],\"permissionsRemoved\":[],"
                + "\"activeFrom\":true,\"activeTo\":false,\"fields\":[\"CARGO\",\"EMAIL\"],"
                + "\"reasons\":[\"ROLE_CHANGED\"]}");
    }

    @Test
    void tamanhoMaximoPossivelFicaMuitoAbaixoDoLimite() {
        // Prova construtiva: mesmo com TODAS as chaves e TODOS os valores possíveis, o metadata cabe no limite.
        String maximo = AuditMetadata.builder()
                .roleChange(Role.SUPER_ADMIN, Role.TENANT_ADMIN)
                .permissions(EnumSet.allOf(Permission.class))
                .permissionsAdded(EnumSet.allOf(Permission.class))
                .permissionsRemoved(EnumSet.allOf(Permission.class))
                .activeChange(false, true)
                .fieldsChanged(EnumSet.allOf(AuditField.class))
                .reasons(EnumSet.allOf(AuditReason.class))
                .build().toJson();
        assertThat(maximo.length()).isLessThan(AuditMetadata.MAX_LENGTH);
        assertThat(AuditMetadata.MAX_LENGTH).isEqualTo(2000);
    }

    /** A API pública NÃO pode aceitar texto livre/objetos genéricos: só enums, boolean, contagem int e Set de enums. */
    @Test
    void apiDoMetadataNaoAceitaStringObjectMapNemGenericos() {
        List<Class<?>> classes = List.of(AuditMetadata.class, AuditMetadata.Builder.class);
        for (Class<?> c : classes) {
            for (Method m : c.getDeclaredMethods()) {
                if (!Modifier.isPublic(m.getModifiers())) {
                    continue;
                }
                Type[] tipos = m.getGenericParameterTypes();
                for (Type t : tipos) {
                    assertThat(permitido(t)).as(c.getSimpleName() + "." + m.getName() + " param " + t).isTrue();
                }
            }
        }
    }

    private boolean permitido(Type t) {
        if (t instanceof Class<?> k) {
            // int: somente contagens (ex.: nº de features alteradas); um número primitivo não carrega texto/segredo.
            return k == boolean.class || k == int.class || k.isEnum();
        }
        if (t instanceof ParameterizedType p && p.getRawType() == Set.class) {
            Type arg = p.getActualTypeArguments()[0];
            return arg instanceof Class<?> k && k.isEnum();
        }
        return false;
    }

    // ---------------- saneamento (User-Agent) ----------------

    @Test
    void userAgentEhLimitadoA200CodePoints() {
        String longo = "A".repeat(5000);
        assertThat(AuditSanitizer.userAgent(longo)).hasSize(AuditSanitizer.MAX_USER_AGENT);
    }

    @Test
    void truncagemNaoCortaParSubstituto() {
        String emojis = "😀".repeat(300); // cada emoji = 2 chars (surrogate pair)
        String r = AuditSanitizer.userAgent(emojis);
        assertThat(r.codePointCount(0, r.length())).isEqualTo(200);
        assertThat(Character.isHighSurrogate(r.charAt(r.length() - 1))).isFalse();
    }

    @Test
    void caracteresDeControleNaoQuebramLogsNemTelas() {
        String malicioso = "Mozilla/5.0\r\n2026-01-01 ERROR forjado\u0000\u0007‮ fim x";
        String r = AuditSanitizer.userAgent(malicioso);
        assertThat(r).doesNotContain("\r").doesNotContain("\n").doesNotContain("\u0000")
                .doesNotContain("\u0007").doesNotContain("‮").doesNotContain(" ");
        assertThat(r).startsWith("Mozilla/5.0");
    }

    @Test
    void credenciaisNoUserAgentSaoRedigidas() {
        String jwt = "eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiIxIiwidHYiOjB9.assinaturaAssinaturaAssinatura123";
        String hash = "$2a$10$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZ0123456";
        assertThat(AuditSanitizer.userAgent("UA " + jwt + " fim")).doesNotContain("eyJ").contains(AuditSanitizer.REDACTED);
        assertThat(AuditSanitizer.userAgent("UA Bearer abc123def456ghi789")).doesNotContain("abc123def456ghi789");
        assertThat(AuditSanitizer.userAgent("UA " + hash)).doesNotContain("$2a$");
    }

    @Test
    void htmlNaoEhInterpretadoMasFicaComoTextoInerte() {
        // Não se "escapa" na gravação: o valor é texto puro e as telas devem escapar ao exibir.
        assertThat(AuditSanitizer.userAgent("<script>alert(1)</script>")).isEqualTo("<script>alert(1)</script>");
    }

    @Test
    void vazioOuSoEspacosViraNull() {
        assertThat(AuditSanitizer.userAgent(null)).isNull();
        assertThat(AuditSanitizer.userAgent("   \r\n  ")).isNull();
    }
}
