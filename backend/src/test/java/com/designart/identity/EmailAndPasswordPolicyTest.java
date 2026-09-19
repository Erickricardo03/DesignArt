package com.designart.identity;

import com.designart.exception.InvalidRequestException;
import com.designart.security.EmailAddress;
import com.designart.security.PasswordPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Unitários: normalização de e-mail e política central de senha. */
class EmailAndPasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    // ---------------- e-mail ----------------

    @Test
    void emailEhNormalizadoComTrimELowercase() {
        assertThat(EmailAddress.normalizeOrNull("  Joao.Silva@EmpresaA.COM  ")).isEqualTo("joao.silva@empresaa.com");
    }

    @Test
    void lowercaseUsaLocaleRootEntaoNaoDependeDoIdiomaDoServidor() {
        // Em locale turco, "I".toLowerCase() viraria "ı" (i sem ponto). Com Locale.ROOT vira "i".
        java.util.Locale original = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"));
            assertThat(EmailAddress.normalizeOrNull("INFO@EMPRESA.COM")).isEqualTo("info@empresa.com");
        } finally {
            java.util.Locale.setDefault(original);
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "sem-arroba", "@sem-local.com", "sem-dominio@", "a@b", "dois@@arrobas.com",
            "com espaco@teste.local", "a@teste..local", "a@.teste.local", "a@teste.local."})
    void emailInvalidoRetornaNull(String raw) {
        assertThat(EmailAddress.normalizeOrNull(raw)).isNull();
    }

    @Test
    void emailLongoDemaisEhInvalido() {
        String local = "a".repeat(65);
        assertThat(EmailAddress.normalizeOrNull(local + "@teste.local")).isNull();
        assertThat(EmailAddress.normalizeOrNull("a".repeat(64) + "@teste.local")).isNotNull();
    }

    // ---------------- senha ----------------

    @Test
    void senhaComDezCaracteresEValida() {
        assertThatCode(() -> policy.validate("abcdefghij", "x@teste.local")).doesNotThrowAnyException();
    }

    @Test
    void senhaComNoveCaracteresEInvalida() {
        assertThatThrownBy(() -> policy.validate("abcdefghi", "x@teste.local"))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("mínimo");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"          "})
    void senhaVaziaOuSoEspacosEInvalida(String senha) {
        assertThatThrownBy(() -> policy.validate(senha, "x@teste.local")).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void limiteEmBytesUtf8_72Passa_73Falha() {
        assertThatCode(() -> policy.validate("a".repeat(72), "x@teste.local")).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.validate("a".repeat(73), "x@teste.local"))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("72 bytes");
    }

    @Test
    void limiteContaBytesENaoCaracteres() {
        // "é" ocupa 2 bytes em UTF-8: 36 = 72 bytes (ok); 37 caracteres = 74 bytes (rejeitada,
        // embora tenha bem menos de 72 caracteres).
        String trintaESeis = "é".repeat(36);
        String trintaESete = "é".repeat(37);
        assertThat(trintaESeis.length()).isEqualTo(36);
        assertThatCode(() -> policy.validate(trintaESeis, "x@teste.local")).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.validate(trintaESete, "x@teste.local"))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("72 bytes");
        // Emoji: 4 bytes cada (surrogate pair): 18 emojis = 72 bytes, 19 = 76.
        assertThatCode(() -> policy.validate("😀".repeat(18), "x@teste.local")).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.validate("😀".repeat(19), "x@teste.local"))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void minimoContaCodePointsEntaoEmojiConta() {
        assertThatCode(() -> policy.validate("😀".repeat(10), "x@teste.local")).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.validate("😀".repeat(9), "x@teste.local"))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void senhaIgualAoEmailNormalizadoEInvalida() {
        assertThatThrownBy(() -> policy.validate("maria.souza@teste.local", "maria.souza@teste.local"))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("e-mail");
        // também com caixa diferente e espaços nas pontas
        assertThatThrownBy(() -> policy.validate("  Maria.Souza@TESTE.local ", "maria.souza@teste.local"))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void caractereNulEInvalido() {
        assertThatThrownBy(() -> policy.validate("senha-valida\u0000-x", "x@teste.local"))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void mensagemDeErroNuncaContemASenha() {
        String senha = "curta";
        assertThatThrownBy(() -> policy.validate(senha, "x@teste.local"))
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(senha));
    }
}
