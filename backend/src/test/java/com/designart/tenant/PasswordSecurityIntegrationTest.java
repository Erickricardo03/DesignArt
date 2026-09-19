package com.designart.tenant;

import com.designart.dto.UsuarioRequest;
import com.designart.model.Tenant;
import com.designart.model.User;
import com.designart.repository.TenantRepository;
import com.designart.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
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
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * A senha armazenada (hash) NUNCA autentica, e nenhum endpoint expõe
 * password/hash. Os hashes são lidos só para uso interno dos testes e jamais
 * impressos.
 */
@SpringBootTest(properties = "spring.profiles.active=test")
@ActiveProfiles("test")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PasswordSecurityIntegrationTest {

    static final String SENHA = "senha-de-teste-123";
    static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void senhaCorretaAutentica() throws Exception {
        criarUsuario("pw.correta", novoTenant("ATIVO"), true);
        assertThat(tentarLogin("pw.correta", SENHA).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void senhaIncorretaRetorna401() throws Exception {
        criarUsuario("pw.errada", novoTenant("ATIVO"), true);
        MvcResult r = tentarLogin("pw.errada", "outra-senha");
        assertThat(r.getResponse().getStatus()).isEqualTo(401);
        assertThat(corpo(r)).contains("Usuário ou senha inválidos.");
    }

    @Test
    void fornecerOHashArmazenadoComoSenhaRetorna401() throws Exception {
        criarUsuario("pw.hash", novoTenant("ATIVO"), true);
        String hash = userRepository.findByUsername("pw.hash").orElseThrow().getPassword();
        assertThat(hash).isNotEqualTo(SENHA); // realmente armazenado como hash
        MvcResult r = tentarLogin("pw.hash", hash);
        assertThat(r.getResponse().getStatus()).isEqualTo(401);
        assertThat(corpo(r)).doesNotContain("token");
        // ... e a senha real continua funcionando.
        assertThat(tentarLogin("pw.hash", SENHA).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void valorLegadoEmTextoPuroNoBancoNaoAutentica() throws Exception {
        // Sem compatibilidade insegura: se um registro antigo tiver a senha em texto puro
        // (fora do formato BCrypt), digitar exatamente esse valor NÃO abre sessão.
        userRepository.save(User.builder().username("pw.legado").password("texto-puro-legado")
                .role("ADMIN").ativo(true).tenantId(novoTenant("ATIVO").getId()).build());
        assertThat(tentarLogin("pw.legado", "texto-puro-legado").getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void usuarioInativoContinuaRetornando401MesmoComSenhaCorreta() throws Exception {
        criarUsuario("pw.inativo", novoTenant("ATIVO"), false);
        assertThat(tentarLogin("pw.inativo", SENHA).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void tenantSuspensoContinuaRetornando401MesmoComSenhaCorreta() throws Exception {
        criarUsuario("pw.suspenso", novoTenant("SUSPENSO"), true);
        assertThat(tentarLogin("pw.suspenso", SENHA).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void respostasNuncaContemPasswordNemHash() throws Exception {
        Tenant t = novoTenant("ATIVO");
        criarUsuario("pw.admin", t, true);
        criarUsuario("pw.colega", t, true);
        String hashAdmin = userRepository.findByUsername("pw.admin").orElseThrow().getPassword();
        String hashColega = userRepository.findByUsername("pw.colega").orElseThrow().getPassword();

        // login
        MvcResult login = tentarLogin("pw.admin", SENHA);
        semSegredo(corpo(login), hashAdmin, hashColega);
        String token = json.readTree(corpo(login)).get("token").asText();

        // /api/auth/me
        semSegredo(corpoOk(get("/api/auth/me"), token), hashAdmin, hashColega);

        // listagem de usuários
        semSegredo(corpoOk(get("/api/usuarios"), token), hashAdmin, hashColega);

        // criação e atualização de usuário
        long colegaId = userRepository.findByUsername("pw.colega").orElseThrow().getId();
        String criado = corpoOk(post("/api/usuarios").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", "pw.novo", "password", "nova-senha-999"))), token);
        semSegredo(criado, hashAdmin, hashColega, "nova-senha-999",
                userRepository.findByUsername("pw.novo").orElseThrow().getPassword());
        String atualizado = corpoOk(put("/api/usuarios/" + colegaId).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("nomeCompleto", "Colega", "password", "outra-senha-888"))), token);
        semSegredo(atualizado, hashAdmin, "outra-senha-888",
                userRepository.findByUsername("pw.colega").orElseThrow().getPassword());
    }

    @Test
    void novasSenhasSaoArmazenadasComoBCryptEAutenticam() throws Exception {
        Tenant t = novoTenant("ATIVO");
        criarUsuario("pw.gestor", t, true);
        String token = json.readTree(corpo(tentarLogin("pw.gestor", SENHA))).get("token").asText();
        corpoOk(post("/api/usuarios").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", "pw.criado", "password", "senha-criada-777"))), token);
        String armazenado = userRepository.findByUsername("pw.criado").orElseThrow().getPassword();
        assertThat(armazenado).startsWith("$2").isNotEqualTo("senha-criada-777");
        assertThat(tentarLogin("pw.criado", "senha-criada-777").getResponse().getStatus()).isEqualTo(200);
        assertThat(tentarLogin("pw.criado", armazenado).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void toStringNaoImprimeSenhaNemHash() {
        User u = User.builder().username("x").password("SEGREDO-NO-USER").build();
        assertThat(u.toString()).doesNotContain("SEGREDO-NO-USER");
        UsuarioRequest req = UsuarioRequest.builder().username("x").password("SEGREDO-NO-REQUEST").build();
        assertThat(req.toString()).doesNotContain("SEGREDO-NO-REQUEST");
        com.designart.dto.LoginRequest lr = com.designart.dto.LoginRequest.builder().username("x")
                .password("SEGREDO-NO-LOGIN").build();
        assertThat(lr.toString()).doesNotContain("SEGREDO-NO-LOGIN");
    }

    @Test
    void entidadeUserNuncaSerializaPasswordEmJson() throws Exception {
        User u = User.builder().username("x").password("SEGREDO-JSON").build();
        String s = json.writeValueAsString(u);
        assertThat(s).doesNotContain("SEGREDO-JSON").doesNotContain("password");
    }

    // ------------------------------------------------------------------
    private void semSegredo(String corpoResposta, String... segredos) throws Exception {
        assertThat(corpoResposta.toLowerCase()).doesNotContain("password").doesNotContain("senha\":");
        for (String segredo : segredos) {
            assertThat(corpoResposta).doesNotContain(segredo);
        }
        assertThat(corpoResposta).doesNotContain("$2a$").doesNotContain("$2b$").doesNotContain("$2y$");
        JsonNode n = json.readTree(corpoResposta);
        assertThat(n.findValues("password")).isEmpty();
    }

    private Tenant novoTenant(String status) {
        int n = SEQ.incrementAndGet();
        return tenantRepository.save(Tenant.builder().name("Tenant pw " + n).slug("pw-teste-" + n).status(status).build());
    }

    private void criarUsuario(String username, Tenant t, boolean ativo) {
        userRepository.save(User.builder().username(username).password(passwordEncoder.encode(SENHA))
                .nomeCompleto(username).role("ADMIN").ativo(ativo).tenantId(t.getId()).build());
    }

    private MvcResult tentarLogin(String username, String senha) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", username, "password", senha)))).andReturn();
    }

    private String corpo(MvcResult r) throws Exception {
        return r.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String corpoOk(MockHttpServletRequestBuilder req, String token) throws Exception {
        MvcResult r = mvc.perform(req.header("Authorization", "Bearer " + token)).andReturn();
        assertThat(r.getResponse().getStatus()).as(req.toString()).isEqualTo(200);
        return corpo(r);
    }
}
