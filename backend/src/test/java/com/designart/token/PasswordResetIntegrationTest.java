package com.designart.token;

import com.designart.audit.AuditAction;
import com.designart.audit.AuditEvent;
import com.designart.identity.IntegrationTestBase;
import com.designart.mail.EmailMessage;
import com.designart.model.Tenant;
import com.designart.model.User;
import com.designart.security.Role;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Recuperação de senha: forgot-password, tokens (hash, expiração, uso único) e reset-password. */
@ExtendWith(OutputCaptureExtension.class)
class PasswordResetIntegrationTest extends IntegrationTestBase {

    static final String NOVA = "Nova-Senha-Segura-2026";

    @Autowired ActionTokenService tokenService;
    @Autowired PlatformTransactionManager txManager;

    private String base(MvcResult r) throws Exception {
        return corpo(r);
    }

    private List<AuditEvent> eventos(long userId, AuditAction acao) {
        return auditRepository.findByTargetUserIdOrderByIdAsc(userId).stream().filter(e -> e.getAction() == acao).toList();
    }

    // ------------------------------------------------------------------ FORGOT
    @Test
    void contaExistente_geraTokenEEnviaEmailComLinkDoAppPublicUrl() throws Exception {
        Tenant t = novoTenant("ATIVO");
        User u = criarUsuario("fp.existente@teste.local", Role.USER, t.getId());
        long antes = maxAuditId();

        MvcResult r = mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"  FP.Existente@TESTE.local \"}").with(deIp("203.0.113.50"))).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(202);
        assertThat(corpoJson(r).get("message").asText()).startsWith("Se existir uma conta elegível");

        EmailMessage m = emails.lastTo("fp.existente@teste.local");
        assertThat(m).isNotNull();
        assertThat(m.subject()).isEqualTo("Redefinição de senha - Nexus Design");
        String token = CapturingTokens.of(m);
        assertThat(m.textBody()).contains("https://app.teste.local/reset-password?token=" + token);
        assertThat(token).matches("^[A-Za-z0-9_-]{43}$");

        List<UserActionToken> linhas = tokenRepository.findByUserIdOrderByIdAsc(u.getId());
        assertThat(linhas).hasSize(1);
        UserActionToken linha = linhas.get(0);
        assertThat(linha.getPurpose()).isEqualTo(ActionTokenPurpose.PASSWORD_RESET);
        assertThat(linha.getRequestedIp()).isEqualTo("203.0.113.50");
        assertThat(linha.getCreatedByUserId()).isNull();
        assertThat(java.time.Duration.between(linha.getCreatedAt(), linha.getExpiresAt())).isEqualTo(Duration.ofMinutes(30));
        // Somente o SHA-256 do token existe no banco: o token puro NUNCA é gravado.
        assertThat(linha.getTokenHash()).isEqualTo(TokenCodec.sha256Hex(token)).isNotEqualTo(token);
        assertThat(dumpTokens()).doesNotContain(token);

        // Auditoria: solicitação para conta real elegível, sem o token.
        List<AuditEvent> novos = auditRepository.findAllByOrderByIdAsc().stream().filter(e -> e.getId() > antes).toList();
        assertThat(novos.stream().filter(e -> e.getAction() == AuditAction.PASSWORD_RESET_REQUESTED).toList()).hasSize(1);
        assertThat(dumpAuditoria()).doesNotContain(token);
    }

    @Test
    void respostaEEquivalenteEmTodosOsCasos_semEnumeracao() throws Exception {
        Tenant ativo = novoTenant("ATIVO");
        Tenant suspenso = novoTenant("SUSPENSO");
        criarUsuario("fp.eq.ativo@teste.local", Role.USER, ativo.getId());
        criarUsuario("fp.eq.inativo@teste.local", Role.USER, ativo.getId(), false, SENHA);
        criarUsuario("fp.eq.suspenso@teste.local", Role.USER, suspenso.getId());
        criarUsuario("fp.eq.super@teste.local", Role.SUPER_ADMIN, null);
        // convite pendente: sem senha, inativo
        userRepository.save(User.builder().email("fp.eq.pendente@teste.local").password(null).ativo(false)
                .role(Role.USER).tenantId(ativo.getId()).build());

        List<String> entradas = List.of("fp.eq.ativo@teste.local", "fp.eq.super@teste.local", "fp.eq.inexistente@teste.local",
                "fp.eq.inativo@teste.local", "fp.eq.suspenso@teste.local", "fp.eq.pendente@teste.local",
                "isto nao e email", "  ", "a".repeat(100) + "@teste.local");
        long tokensAntes = tokenRepository.count();
        long eventosAntes = maxAuditId();
        MvcResult referencia = forgot("fp.eq.inexistente@teste.local");
        String corpoReferencia = base(referencia);
        for (String e : entradas) {
            if (e.isBlank()) {
                continue; // vazio é erro de validação de entrada (400), não um caso de conta
            }
            MvcResult r = forgot(e);
            assertThat(r.getResponse().getStatus()).as(e).isEqualTo(202);
            assertThat(base(r)).as("corpo idêntico para " + e).isEqualTo(corpoReferencia);
            assertThat(r.getResponse().getContentType()).isEqualTo(referencia.getResponse().getContentType());
        }
        // Só as contas ELEGÍVEIS (ativa e SUPER_ADMIN ativo) receberam e-mail/token/auditoria.
        assertThat(emails.sentTo("fp.eq.ativo@teste.local")).hasSize(1);
        assertThat(emails.sentTo("fp.eq.super@teste.local")).hasSize(1);
        for (String naoElegivel : List.of("fp.eq.inexistente@teste.local", "fp.eq.inativo@teste.local",
                "fp.eq.suspenso@teste.local", "fp.eq.pendente@teste.local")) {
            assertThat(emails.sentTo(naoElegivel)).as(naoElegivel).isEmpty();
        }
        assertThat(emails.all()).hasSize(2);
        assertThat(tokenRepository.count() - tokensAntes).isEqualTo(2);
        long solicitacoes = auditRepository.findAllByOrderByIdAsc().stream()
                .filter(e -> e.getId() > eventosAntes && e.getAction() == AuditAction.PASSWORD_RESET_REQUESTED).count();
        assertThat(solicitacoes).isEqualTo(2); // nunca para conta inexistente/inelegível
    }

    @Test
    void novoPedidoRevogaOTokenAnterior_soOUltimoFunciona() throws Exception {
        Tenant t = novoTenant("ATIVO");
        User u = criarUsuario("fp.revoga@teste.local", Role.USER, t.getId());
        forgot("fp.revoga@teste.local");
        String primeiro = emails.lastToken("fp.revoga@teste.local");
        forgot("fp.revoga@teste.local");
        String segundo = emails.lastToken("fp.revoga@teste.local");
        assertThat(segundo).isNotEqualTo(primeiro);

        List<UserActionToken> linhas = tokenRepository.findByUserIdOrderByIdAsc(u.getId());
        assertThat(linhas).hasSize(2);
        assertThat(linhas.get(0).getRevokedAt()).isNotNull();   // anterior revogado
        assertThat(linhas.get(1).getRevokedAt()).isNull();      // novo ativo

        assertThat(resetar(primeiro, NOVA, NOVA).getResponse().getStatus()).isEqualTo(400);
        assertThat(resetar(segundo, NOVA, NOVA).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void tokensSaoImprevisiveisEUnicos() throws Exception {
        Tenant t = novoTenant("ATIVO");
        criarUsuario("fp.entropia@teste.local", Role.USER, t.getId());
        Set<String> vistos = new HashSet<>();
        for (int i = 0; i < 25; i++) {
            forgot("fp.entropia@teste.local");
            String token = emails.lastToken("fp.entropia@teste.local");
            assertThat(token).matches("^[A-Za-z0-9_-]{43}$");
            assertThat(vistos.add(token)).isTrue();
        }
    }

    // ------------------------------------------------------------------ RESET (sucesso)
    @Test
    void reset_trocaSenhaComBcrypt_invalidaJwtAntigo_revogaOutrosTokens_eAudita() throws Exception {
        Tenant t = novoTenant("ATIVO");
        User u = criarUsuario("rp.sucesso@teste.local", Role.USER, t.getId());
        String jwtAntigo = login("rp.sucesso@teste.local");
        assertThat(status(get("/api/auth/me"), jwtAntigo)).isEqualTo(200);
        String hashAntigo = userRepository.findById(u.getId()).orElseThrow().getPassword();
        int versaoAntes = userRepository.findById(u.getId()).orElseThrow().getTokenVersion();

        // um token de OUTRA finalidade ativo para o mesmo usuário deve ser revogado pelo reset
        new TransactionTemplate(txManager).executeWithoutResult(s -> tokenService.issue(u.getId(), ActionTokenPurpose.INVITE, null, null));
        // e uma tentativa de login falha deixa contador para zerar
        tentarLogin("rp.sucesso@teste.local", "senha-errada-999");
        forgot("rp.sucesso@teste.local");
        String token = emails.lastToken("rp.sucesso@teste.local");
        emails.clear();
        long antes = maxAuditId();

        MvcResult r = resetar(token, NOVA, NOVA);
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        assertThat(base(r)).doesNotContain(NOVA).doesNotContain(token).doesNotContain("$2");

        User depois = userRepository.findById(u.getId()).orElseThrow();
        assertThat(depois.getPassword()).startsWith("$2").isNotEqualTo(NOVA).isNotEqualTo(hashAntigo);  // BCrypt
        assertThat(passwordEncoder.matches(NOVA, depois.getPassword())).isTrue();
        assertThat(depois.getTokenVersion()).isEqualTo(versaoAntes + 1);                                 // token_version++
        assertThat(depois.getFailedLogins()).isZero();
        assertThat(depois.getLockedUntil()).isNull();
        // JWT emitido antes deixa de funcionar imediatamente
        assertThat(status(get("/api/auth/me"), jwtAntigo)).isEqualTo(401);
        // senha antiga não entra mais; a nova entra
        assertThat(tentarLogin("rp.sucesso@teste.local", SENHA).getResponse().getStatus()).isEqualTo(401);
        assertThat(tentarLogin("rp.sucesso@teste.local", NOVA).getResponse().getStatus()).isEqualTo(200);

        // demais tokens ativos do usuário revogados; o consumido está "usado", não "revogado"
        for (UserActionToken linha : tokenRepository.findByUserIdOrderByIdAsc(u.getId())) {
            assertThat(linha.isActive(java.time.LocalDateTime.now(clock))).isFalse();
        }
        assertThat(tokenRepository.findByUserIdOrderByIdAsc(u.getId()).stream().filter(l -> l.getUsedAt() != null)).hasSize(1);

        // auditoria: PASSWORD_RESET_COMPLETED + SESSIONS_REVOKED (PASSWORD_CHANGED), sem segredos
        List<AuditEvent> novos = auditRepository.findAllByOrderByIdAsc().stream().filter(e -> e.getId() > antes).toList();
        AuditEvent concluido = novos.stream().filter(e -> e.getAction() == AuditAction.PASSWORD_RESET_COMPLETED).findFirst().orElseThrow();
        assertThat(concluido.getTargetUserId()).isEqualTo(u.getId());
        assertThat(concluido.getActorUserId()).isEqualTo(u.getId());
        AuditEvent revogacao = novos.stream().filter(e -> e.getAction() == AuditAction.SESSIONS_REVOKED).findFirst().orElseThrow();
        assertThat(revogacao.getMetadata()).contains("PASSWORD_CHANGED");
        assertThat(dumpAuditoria()).doesNotContain(token).doesNotContain(NOVA).doesNotContain(depois.getPassword());

        // aviso de "senha alterada" (sem token nem link)
        EmailMessage aviso = emails.lastTo("rp.sucesso@teste.local");
        assertThat(aviso).isNotNull();
        assertThat(aviso.subject()).contains("alterada");
        assertThat(aviso.textBody()).doesNotContain("token=").doesNotContain(NOVA);
    }

    // ------------------------------------------------------------------ RESET (validações)
    @Test
    void reset_politicaDeSenhaEConfirmacao_naoQueimamOToken() throws Exception {
        Tenant t = novoTenant("ATIVO");
        criarUsuario("rp.politica@teste.local", Role.USER, t.getId());
        forgot("rp.politica@teste.local");
        String token = emails.lastToken("rp.politica@teste.local");

        assertThat(resetar(token, "curta", "curta").getResponse().getStatus()).isEqualTo(400);                        // < 10
        assertThat(resetar(token, "a".repeat(73), "a".repeat(73)).getResponse().getStatus()).isEqualTo(400);           // 73 bytes
        assertThat(resetar(token, "é".repeat(37), "é".repeat(37)).getResponse().getStatus()).isEqualTo(400);           // 74 bytes UTF-8
        assertThat(resetar(token, "rp.politica@teste.local", "rp.politica@teste.local").getResponse().getStatus()).isEqualTo(400); // = e-mail
        assertThat(resetar(token, NOVA, NOVA + "x").getResponse().getStatus()).isEqualTo(400);                          // confirmação
        // O mesmo link segue válido: nada acima o consumiu.
        assertThat(resetar(token, "a".repeat(72), "a".repeat(72)).getResponse().getStatus()).isEqualTo(200);          // 72 bytes: ok
        assertThat(tentarLogin("rp.politica@teste.local", "a".repeat(72)).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void reset_tokenExpiradoRevogadoUsadoOuInexistente_respondemIgual() throws Exception {
        Tenant t = novoTenant("ATIVO");
        criarUsuario("rp.invalidos@teste.local", Role.USER, t.getId());

        // expirado (30 min)
        forgot("rp.invalidos@teste.local");
        String expirado = emails.lastToken("rp.invalidos@teste.local");
        clock.advance(Duration.ofMinutes(29));
        forgot("rp.invalidos@teste.local"); // (revoga o anterior) — para provar que 29 min ainda valeria, usamos outro abaixo
        clock.reset();

        // usado (replay)
        forgot("rp.invalidos@teste.local");
        String usado = emails.lastToken("rp.invalidos@teste.local");
        assertThat(resetar(usado, NOVA, NOVA).getResponse().getStatus()).isEqualTo(200);
        MvcResult replay = resetar(usado, "Outra-Senha-Segura-2027", "Outra-Senha-Segura-2027");

        // revogado (novo pedido revoga)
        forgot("rp.invalidos@teste.local");
        String revogado = emails.lastToken("rp.invalidos@teste.local");
        forgot("rp.invalidos@teste.local");

        // expirado de verdade
        forgot("rp.invalidos@teste.local");
        String vencido = emails.lastToken("rp.invalidos@teste.local");
        clock.advance(Duration.ofMinutes(31));

        MvcResult mExpirado = resetar(vencido, NOVA, NOVA);
        MvcResult mRevogado = resetar(revogado, NOVA, NOVA);
        MvcResult mInexistente = resetar(TokenCodec.generate(), NOVA, NOVA);
        MvcResult mLixo = resetar("lixo", NOVA, NOVA);
        MvcResult mPrimeiroExpirado = resetar(expirado, NOVA, NOVA);

        String referencia = corpoJson(replay).get("message").asText();
        assertThat(referencia).isEqualTo("Link inválido ou expirado. Solicite uma nova recuperação de senha.");
        for (MvcResult r : List.of(replay, mExpirado, mRevogado, mInexistente, mLixo, mPrimeiroExpirado)) {
            assertThat(r.getResponse().getStatus()).isEqualTo(400);
            assertThat(corpoJson(r).get("message").asText()).isEqualTo(referencia); // nada revela o motivo
        }
    }

    @Test
    void reset_tokenValidoAte30Minutos() throws Exception {
        Tenant t = novoTenant("ATIVO");
        criarUsuario("rp.limite@teste.local", Role.USER, t.getId());
        forgot("rp.limite@teste.local");
        String token = emails.lastToken("rp.limite@teste.local");
        clock.advance(Duration.ofMinutes(29).plusSeconds(30));
        assertThat(resetar(token, NOVA, NOVA).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void reset_finalidadeIncorreta_conviteNaoServeParaReset_eViceVersa() throws Exception {
        Tenant t = novoTenant("ATIVO");
        User u = criarUsuario("rp.finalidade@teste.local", Role.USER, t.getId());
        IssuedToken[] convite = new IssuedToken[1];
        new TransactionTemplate(txManager).executeWithoutResult(s ->
                convite[0] = tokenService.issue(u.getId(), ActionTokenPurpose.INVITE, null, null));

        // token de CONVITE usado no reset-password: recusado...
        assertThat(resetar(convite[0].rawToken(), NOVA, NOVA).getResponse().getStatus()).isEqualTo(400);
        // ...e não foi consumido por engano.
        assertThat(tokenRepository.findByUserIdOrderByIdAsc(u.getId()).get(0).getUsedAt()).isNull();

        // token de RESET usado no accept-invite: recusado (e continua válido para o reset).
        forgot("rp.finalidade@teste.local");
        String reset = emails.lastToken("rp.finalidade@teste.local");
        assertThat(aceitarConvite(reset, NOVA, NOVA).getResponse().getStatus()).isEqualTo(400);
        assertThat(resetar(reset, NOVA, NOVA).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void reset_usuarioDesativadoOuTenantSuspensoDepoisDoPedido_falhaFechado() throws Exception {
        Tenant t = novoTenant("ATIVO");
        User u = criarUsuario("rp.elegib@teste.local", Role.USER, t.getId());
        forgot("rp.elegib@teste.local");
        String token = emails.lastToken("rp.elegib@teste.local");

        User x = userRepository.findById(u.getId()).orElseThrow();
        x.setAtivo(false);
        userRepository.save(x);
        assertThat(resetar(token, NOVA, NOVA).getResponse().getStatus()).isEqualTo(400);   // usuário inativo
        x.setAtivo(true);
        userRepository.save(x);

        t.setStatus("SUSPENSO");
        tenantRepository.save(t);
        assertThat(resetar(token, NOVA, NOVA).getResponse().getStatus()).isEqualTo(400);   // tenant suspenso
        t.setStatus("ATIVO");
        tenantRepository.save(t);

        // Nada foi consumido nas tentativas negadas.
        assertThat(resetar(token, NOVA, NOVA).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void desativarUsuarioRevogaTokensPendentes() throws Exception {
        Tenant t = novoTenant("ATIVO");
        criarUsuario("rp.admin.desativa@teste.local", Role.TENANT_ADMIN, t.getId());
        User alvo = criarUsuario("rp.alvo.desativa@teste.local", Role.USER, t.getId());
        String admin = login("rp.admin.desativa@teste.local");
        forgot("rp.alvo.desativa@teste.local");
        String token = emails.lastToken("rp.alvo.desativa@teste.local");

        assertThat(status(put("/api/usuarios/" + alvo.getId()).contentType(MediaType.APPLICATION_JSON).content("{\"ativo\":false}"), admin)).isEqualTo(200);
        assertThat(tokenRepository.findByUserIdOrderByIdAsc(alvo.getId()).get(0).getRevokedAt()).isNotNull();
        assertThat(status(put("/api/usuarios/" + alvo.getId()).contentType(MediaType.APPLICATION_JSON).content("{\"ativo\":true}"), admin)).isEqualTo(200);
        assertThat(resetar(token, NOVA, NOVA).getResponse().getStatus()).isEqualTo(400);  // não ressuscita
    }

    // ------------------------------------------------------------------ e-mail
    @Test
    void headersMaliciososNaoAlteramOLinkDoEmail() throws Exception {
        Tenant t = novoTenant("ATIVO");
        criarUsuario("rp.host@teste.local", Role.USER, t.getId());
        MvcResult r = mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .header("Host", "evil.example.com").header("X-Forwarded-Host", "evil2.example.com")
                .header("X-Forwarded-Proto", "http").header("Origin", "https://evil3.example.com")
                .header("Referer", "https://evil4.example.com/x").header("Forwarded", "host=evil5.example.com")
                .content("{\"email\":\"rp.host@teste.local\"}")).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(202);
        EmailMessage m = emails.lastTo("rp.host@teste.local");
        assertThat(m.textBody()).contains("https://app.teste.local/reset-password?token=")
                .doesNotContain("evil").doesNotContain("http://");
        assertThat(m.htmlBody()).doesNotContain("evil");
    }

    @Test
    void htmlDoEmailEscapaONomeDoUsuario() throws Exception {
        Tenant t = novoTenant("ATIVO");
        User u = criarUsuario("rp.xss@teste.local", Role.USER, t.getId());
        User x = userRepository.findById(u.getId()).orElseThrow();
        x.setNomeCompleto("<script>alert(1)</script> & <img src=x onerror=1>");
        userRepository.save(x);
        forgot("rp.xss@teste.local");
        EmailMessage m = emails.lastTo("rp.xss@teste.local");
        assertThat(m.htmlBody()).doesNotContain("<script>").doesNotContain("<img").contains("&lt;script&gt;");
    }

    @Test
    void tokenNuncaApareceEmLogsNemNasRespostasHttp(CapturedOutput output) throws Exception {
        Tenant t = novoTenant("ATIVO");
        criarUsuario("rp.logs@teste.local", Role.USER, t.getId());
        MvcResult f = forgot("rp.logs@teste.local");
        String token = emails.lastToken("rp.logs@teste.local");
        MvcResult r1 = resetar(token, "curta", "curta");
        MvcResult r2 = resetar(token, NOVA, NOVA);
        MvcResult r3 = resetar(token, NOVA, NOVA); // replay
        for (MvcResult r : List.of(f, r1, r2, r3)) {
            assertThat(base(r)).doesNotContain(token).doesNotContain(NOVA);
            assertThat(r.getResponse().getHeaderNames().stream().map(n -> r.getResponse().getHeader(n))
                    .filter(java.util.Objects::nonNull).toList().toString()).doesNotContain(token);
        }
        assertThat(output.getAll()).doesNotContain(token).doesNotContain(NOVA).doesNotContain(TokenCodec.sha256Hex(token));
    }

    // ------------------------------------------------------------------ JWT e SUPER_ADMIN
    @Test
    void superAdminRecuperaSenhaPeloMesmoFluxo() throws Exception {
        criarUsuario("rp.super@teste.local", Role.SUPER_ADMIN, null);
        forgot("rp.super@teste.local");
        String token = emails.lastToken("rp.super@teste.local");
        assertThat(token).isNotNull();
        assertThat(resetar(token, NOVA, NOVA).getResponse().getStatus()).isEqualTo(200);
        assertThat(tentarLogin("rp.super@teste.local", NOVA).getResponse().getStatus()).isEqualTo(200);
    }

    // ------------------------------------------------------------------ helper
    /** Extrai o token do e-mail capturado (memória do teste). */
    static final class CapturingTokens {
        static String of(EmailMessage m) {
            return com.designart.support.CapturingEmailSender.tokenOf(m);
        }
    }
}
