package com.designart.audit;

import com.designart.identity.IntegrationTestBase;
import com.designart.model.Tenant;
import com.designart.model.User;
import com.designart.security.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * Teste de segurança ADVERSARIAL: tenta deliberadamente fazer valores sensíveis chegarem à trilha de
 * auditoria por todos os caminhos possíveis (campo de e-mail, senhas, header Authorization,
 * User-Agent, X-Forwarded-For, corpo, tokens de convite e de recuperação, segredos de configuração).
 * FALHA se a infraestrutura permitir que qualquer um apareça em QUALQUER coluna de QUALQUER linha.
 */
class AuditSecretsIntegrationTest extends IntegrationTestBase {

    // Marcadores únicos e reconhecíveis.
    private static final String SENHA_LOGIN = "Senha-Real-Do-Login-Marcador-1111";
    private static final String SENHA_ERRADA = "Senha-Errada-Marcador-2222";
    private static final String SENHA_ACEITE = "Senha-Definida-No-Aceite-Marcador-3333";
    private static final String SENHA_RESET = "Senha-Trocada-No-Reset-Marcador-4444";
    private static final String SENHA_NO_EMAIL = "SenhaDigitadaNoCampoEmail-Marcador-5555";
    private static final String MARCADOR_XFF = "xff-com-segredo-Marcador-6666";
    private static final String MARCADOR_CORPO = "corpo-gigante-com-segredo-Marcador-7777";
    private static final String JWT_SECRET_DE_TESTE = "test-only-secret-test-only-secret-test-only-secret-0123456789";

    @Test
    void nenhumSegredoConhecidoChegaAQualquerColunaDaAuditoria() throws Exception {
        Tenant t = novoTenant("ATIVO");
        User admin = criarUsuario("segredos.admin@teste.local", Role.TENANT_ADMIN, t.getId(), true, SENHA_LOGIN);
        String hashAdmin = userRepository.findById(admin.getId()).orElseThrow().getPassword();

        // 1) login real -> obtém um JWT real
        MvcResult ok = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", "segredos.admin@teste.local", "password", SENHA_LOGIN)))).andReturn();
        String jwt = corpoJson(ok).get("token").asText();
        assertThat(jwt).startsWith("eyJ");

        // 2) tentativas de vazar via campo de e-mail / senha errada / anônimo
        for (String entrada : List.of(SENHA_NO_EMAIL, "eyJ" + "a".repeat(30) + "." + "b".repeat(30), "$2a$10$" + "c".repeat(53))) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("email", entrada, "password", SENHA_ERRADA)))
                    .header("User-Agent", "UA-normal/1.0")).andReturn();
        }
        tentarLogin("segredos.admin@teste.local", SENHA_ERRADA); // conta EXISTENTE com senha errada

        // 3) User-Agent carregando o JWT, Bearer, o hash e controles
        String[] uas = {
                "Mozilla/5.0 Bearer " + jwt,
                jwt,
                "Cliente " + hashAdmin,
                "UA\r\nAuthorization: Bearer " + jwt + "\r\nX: y", // CR/LF em header já é barrado pelo firewall
                "Basic dXNlcjpwYXNzd29yZA==",
        };
        for (String ua : uas) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("email", "segredos.admin@teste.local", "password", SENHA_LOGIN)))
                    .header("User-Agent", ua)).andReturn();
        }

        // 4) CONVITE com Authorization, XFF e corpo com marcadores; captura o token do e-mail (memória do teste)
        MvcResult convite = executar(post("/api/usuarios").contentType(MediaType.APPLICATION_JSON)
                .header("X-Forwarded-For", MARCADOR_XFF).header("User-Agent", "Bearer " + jwt)
                .content(json.writeValueAsString(Map.of("email", "segredos.alvo@teste.local", "password", "ignorada-pelo-servidor",
                        "nomeCompleto", MARCADOR_CORPO, "cargo", MARCADOR_CORPO, "role", "USER"))), jwt);
        assertThat(convite.getResponse().getStatus()).isEqualTo(200);
        long alvoId = corpoJson(convite).get("id").asLong();
        String tokenConvite = emails.lastToken("segredos.alvo@teste.local");
        assertThat(tokenConvite).hasSize(com.designart.token.TokenCodec.TOKEN_LENGTH);

        // 5) aceite do convite com uma senha; depois recuperação de senha e redefinição
        assertThat(postJson("/api/auth/accept-invite", Map.of("token", tokenConvite, "newPassword", SENHA_ACEITE,
                "confirmPassword", SENHA_ACEITE), null).getResponse().getStatus()).isEqualTo(200);
        String hashAlvo = userRepository.findById(alvoId).orElseThrow().getPassword();
        assertThat(postJson("/api/auth/forgot-password", Map.of("email", "segredos.alvo@teste.local"), null)
                .getResponse().getStatus()).isEqualTo(202);
        String tokenReset = emails.lastToken("segredos.alvo@teste.local");
        assertThat(tokenReset).isNotEqualTo(tokenConvite);
        assertThat(postJson("/api/auth/reset-password", Map.of("token", tokenReset, "newPassword", SENHA_RESET,
                "confirmPassword", SENHA_RESET), null).getResponse().getStatus()).isEqualTo(200);
        String hashAlvoDepois = userRepository.findById(alvoId).orElseThrow().getPassword();
        // tentativa de reutilizar tokens já consumidos
        postJson("/api/auth/reset-password", Map.of("token", tokenReset, "newPassword", SENHA_RESET, "confirmPassword", SENHA_RESET), null);
        executar(delete("/api/usuarios/" + alvoId), jwt);

        // ---- varredura de TODAS as colunas de TODAS as linhas ----
        String tabela = dumpAuditoria();
        assertThat(tabela.length()).isGreaterThan(200);
        List<String> segredos = new ArrayList<>(List.of(
                SENHA_LOGIN, SENHA_ERRADA, SENHA_ACEITE, SENHA_RESET, SENHA_NO_EMAIL, MARCADOR_XFF, MARCADOR_CORPO,
                JWT_SECRET_DE_TESTE, jwt, hashAdmin, hashAlvo, hashAlvoDepois, tokenConvite, tokenReset,
                "ignorada-pelo-servidor", "dXNlcjpwYXNzd29yZA=="));
        for (String segredo : segredos) {
            assertThat(tabela).as("segredo NÃO pode aparecer na auditoria: " + segredo.substring(0, Math.min(12, segredo.length())) + "...")
                    .doesNotContain(segredo);
        }
        // Nenhum padrão de credencial em nenhuma coluna.
        assertThat(tabela).doesNotContain("eyJ").doesNotContain("Bearer").doesNotContain("$2a$").doesNotContain("$2b$")
                .doesNotContain("$2y$");
        assertThat(tabela.toLowerCase()).doesNotContain("password\":").doesNotContain("authorization: ");
        // Dados livres do corpo da requisição nunca são auditados.
        assertThat(tabela).doesNotContain("Marcador");
        // Os novos fluxos ESTÃO na trilha (auditoria sem segredo).
        assertThat(auditRepository.findByTargetUserIdOrderByIdAsc(alvoId).stream().map(AuditEvent::getAction).toList())
                .contains(AuditAction.INVITE_CREATED, AuditAction.INVITE_ACCEPTED, AuditAction.PASSWORD_RESET_REQUESTED,
                        AuditAction.PASSWORD_RESET_COMPLETED);
    }

    @Test
    void naoExisteCaminhoParaGravarTextoLivreNaTrilha() {
        // Os únicos textos livres da API de auditoria são e-mails de snapshot, e precisam ser e-mails válidos.
        for (String segredo : List.of(SENHA_LOGIN, "eyJabc.def.ghi", "$2a$10$abcdefghijklmnopqrstuv", "Bearer x")) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> AuditActor.of(
                    User.builder().id(1L).email(segredo).role(Role.USER).tenantId(1L).build()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
