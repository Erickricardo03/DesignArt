package com.designart.identity;

import com.designart.config.JwtUtil;
import com.designart.model.Tenant;
import com.designart.model.User;
import com.designart.repository.TenantRepository;
import com.designart.repository.UserRepository;
import com.designart.security.Permission;
import com.designart.security.Role;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Base dos testes de identidade (Fase 4.1). Tenants e usuários existem SOMENTE
 * no H2 de teste. Todos os e-mails de teste usam o domínio reservado
 * {@code teste.local}.
 */
@SpringBootTest(properties = "spring.profiles.active=test")
@ActiveProfiles("test")
@AutoConfigureMockMvc
public abstract class IntegrationTestBase {

    protected static final String SENHA = "senha-de-teste-123";
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected TenantRepository tenantRepository;
    @Autowired protected UserRepository userRepository;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected JwtUtil jwtUtil;
    @Autowired protected org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired protected com.designart.audit.AuditEventRepository auditRepository;
    @Autowired protected com.designart.support.CapturingEmailSender emails;
    @Autowired protected com.designart.support.MutableClock clock;
    @Autowired protected com.designart.ratelimit.RateLimitService rateLimit;
    @Autowired protected com.designart.token.UserActionTokenRepository tokenRepository;

    protected Tenant novoTenant(String status) {
        int n = SEQ.incrementAndGet();
        return tenantRepository.save(Tenant.builder().name("Tenant identidade " + n)
                .slug("identidade-teste-" + n).status(status).build());
    }

    protected User criarUsuario(String email, Role role, Long tenantId, Permission... permissoes) {
        return criarUsuario(email, role, tenantId, true, SENHA, permissoes);
    }

    protected User criarUsuario(String email, Role role, Long tenantId, boolean ativo, String senha,
                                Permission... permissoes) {
        Set<Permission> perms = permissoes.length == 0 ? EnumSet.noneOf(Permission.class) : EnumSet.copyOf(java.util.List.of(permissoes));
        return userRepository.save(User.builder().email(email).password(passwordEncoder.encode(senha))
                .nomeCompleto(email).role(role).ativo(ativo).tenantId(tenantId)
                .permissoes(new java.util.HashSet<>(perms)).build());
    }

    protected MvcResult tentarLogin(String email, String senha) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", senha)))).andReturn();
    }

    protected String login(String email) throws Exception {
        MvcResult r = tentarLogin(email, SENHA);
        assertThat(r.getResponse().getStatus()).as("login " + email).isEqualTo(200);
        return json.readTree(corpo(r)).get("token").asText();
    }

    protected int status(MockHttpServletRequestBuilder req, String token) throws Exception {
        return mvc.perform(token == null ? req : req.header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getStatus();
    }

    protected MvcResult executar(MockHttpServletRequestBuilder req, String token) throws Exception {
        return mvc.perform(token == null ? req : req.header("Authorization", "Bearer " + token)).andReturn();
    }

    protected String corpo(MvcResult r) throws Exception {
        return r.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    protected JsonNode corpoJson(MvcResult r) throws Exception {
        return json.readTree(corpo(r));
    }

    /** Maior id atual em audit_events (0 se vazio): marca o "antes" para isolar eventos de um teste. */
    protected long maxAuditId() {
        return jdbc.queryForObject("select coalesce(max(id), 0) from audit_events", Long.class);
    }

    /** Texto de TODAS as colunas de TODAS as linhas de audit_events (para provar ausência de segredos). */
    protected String dumpAuditoria() {
        return jdbc.queryForList("select * from audit_events").stream()
                .map(Object::toString).collect(java.util.stream.Collectors.joining("\n"));
    }

    /** Estado limpo por teste: caixa de e-mail vazia e habilitada, contadores de rate limit zerados, relógio real. */
    @org.junit.jupiter.api.BeforeEach
    void resetarEstadoCompartilhado() {
        emails.clear();
        emails.setEnabled(true);
        emails.setFailing(false);
        rateLimit.reset();
        clock.reset();
    }

    @org.junit.jupiter.api.AfterEach
    void restaurarRelogio() {
        clock.reset();
        emails.setEnabled(true);
        emails.setFailing(false);
    }

    protected MvcResult postJson(String path, Object corpo, String token) throws Exception {
        return executar(post(path).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(corpo)), token);
    }

    /**
     * Cria um usuário pelo fluxo REAL de convite (o administrador não define senha): convida via API,
     * lê o token do e-mail capturado em memória e aceita o convite com a senha padrão de teste.
     * Retorna o id do usuário já ativo.
     */
    protected long convidarEAceitar(String tokenAdmin, String email, Role role, Permission... permissoes) throws Exception {
        Map<String, Object> corpo = new java.util.HashMap<>();
        corpo.put("email", email);
        corpo.put("role", role.name());
        corpo.put("permissoes", java.util.Arrays.stream(permissoes).map(Enum::name).toList());
        MvcResult convite = postJson("/api/usuarios", corpo, tokenAdmin);
        assertThat(convite.getResponse().getStatus()).as("convite: " + corpo(convite)).isEqualTo(200);
        long id = corpoJson(convite).get("id").asLong();
        String token = emails.lastToken(email.trim().toLowerCase());
        assertThat(token).as("token do convite na caixa de e-mail de teste").isNotNull();
        MvcResult aceite = postJson("/api/auth/accept-invite",
                Map.of("token", token, "newPassword", SENHA, "confirmPassword", SENHA), null);
        assertThat(aceite.getResponse().getStatus()).as("aceite: " + corpo(aceite)).isEqualTo(200);
        return id;
    }

    protected MvcResult forgot(String email) throws Exception {
        return postJson("/api/auth/forgot-password", Map.of("email", email), null);
    }

    protected MvcResult resetar(String token, String senha, String confirmacao) throws Exception {
        return postJson("/api/auth/reset-password",
                Map.of("token", token, "newPassword", senha, "confirmPassword", confirmacao), null);
    }

    protected MvcResult aceitarConvite(String token, String senha, String confirmacao) throws Exception {
        return postJson("/api/auth/accept-invite",
                Map.of("token", token, "newPassword", senha, "confirmPassword", confirmacao), null);
    }

    /** Simula o IP de origem da conexão (o rate limit/auditoria usam SOMENTE o IP da conexão por padrão). */
    protected org.springframework.test.web.servlet.request.RequestPostProcessor deIp(String remoteAddr) {
        return r -> {
            r.setRemoteAddr(remoteAddr);
            return r;
        };
    }

    /** Todas as colunas de user_action_tokens como texto (prova de que o token puro nunca é gravado). */
    protected String dumpTokens() {
        return jdbc.queryForList("select * from user_action_tokens").stream()
                .map(Object::toString).collect(java.util.stream.Collectors.joining("\n"));
    }
}
