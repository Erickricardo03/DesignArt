package com.designart.token;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Geração (256 bits, SecureRandom) e hash (SHA-256) dos tokens de ação. */
class TokenCodecTest {

    @Test
    void tokenTem256BitsDeEntropia_base64UrlSemPadding() {
        String token = TokenCodec.generate();
        assertThat(token).hasSize(TokenCodec.TOKEN_LENGTH);
        assertThat(token).matches("^[A-Za-z0-9_-]{43}$");           // URL-safe, sem "=" nem "+" "/"
        byte[] bytes = Base64.getUrlDecoder().decode(token);
        assertThat(bytes).hasSize(TokenCodec.ENTROPY_BYTES).hasSize(32); // 32 bytes = 256 bits
    }

    @Test
    void tokensSaoUnicosEImprevisiveis() {
        Set<String> vistos = new HashSet<>();
        Set<Character> primeirosCaracteres = new HashSet<>();
        for (int i = 0; i < 20_000; i++) {
            String t = TokenCodec.generate();
            assertThat(vistos.add(t)).as("colisão de token na iteração " + i).isTrue();
            primeirosCaracteres.add(t.charAt(0));
        }
        // Sanidade estatística: não pode ser sequencial/constante (o 1º caractere varia bastante).
        assertThat(primeirosCaracteres.size()).isGreaterThan(20);
    }

    @Test
    void distribuicaoDosBitsNaoEViesada() {
        // Cada bit de 32 bytes aleatórios deve ser ~50% 1. Em 4000 tokens (128.000 bits) o desvio é minúsculo.
        long uns = 0;
        long total = 0;
        for (int i = 0; i < 4000; i++) {
            for (byte b : Base64.getUrlDecoder().decode(TokenCodec.generate())) {
                uns += Integer.bitCount(b & 0xFF);
                total += 8;
            }
        }
        double proporcao = (double) uns / total;
        assertThat(proporcao).isBetween(0.49, 0.51);
    }

    @Test
    void sha256VetorConhecido() {
        // SHA-256("abc") — vetor de teste do NIST.
        assertThat(TokenCodec.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void hashTem64CaracteresHexEDiferenteDoToken() {
        String token = TokenCodec.generate();
        String hash = TokenCodec.sha256Hex(token);
        assertThat(hash).matches("^[0-9a-f]{64}$").isNotEqualTo(token).doesNotContain(token);
        assertThat(TokenCodec.sha256Hex(token)).isEqualTo(hash);                   // determinístico
        assertThat(TokenCodec.sha256Hex(token + "x")).isNotEqualTo(hash);
    }

    @Test
    void toStringDoTokenEmitidoNuncaImprimeOValor() {
        IssuedToken emitido = new IssuedToken(7L, "TOKEN-SECRETO-QUE-NAO-PODE-VAZAR", ActionTokenPurpose.INVITE);
        assertThat(emitido.toString()).doesNotContain("TOKEN-SECRETO-QUE-NAO-PODE-VAZAR").contains("REDACTED");
        assertThat(String.valueOf(emitido)).doesNotContain("TOKEN-SECRETO");
        assertThat(("" + emitido).getBytes(StandardCharsets.UTF_8)).isNotEmpty();
    }

    @Test
    void validadePorFinalidade() {
        assertThat(ActionTokenPurpose.PASSWORD_RESET.ttl().toMinutes()).isEqualTo(30);
        assertThat(ActionTokenPurpose.INVITE.ttl().toHours()).isEqualTo(72);
    }
}
