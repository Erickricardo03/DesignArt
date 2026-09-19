package com.designart.ratelimit;

import com.designart.identity.IntegrationTestBase;
import com.designart.model.Tenant;
import com.designart.security.Role;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Rate limiting nos endpoints públicos e de convite, com limites baixos configurados por propriedade. */
@TestPropertySource(properties = {
        "nexus.ratelimit.login-ip.max=1000",
        "nexus.ratelimit.login-ip-identity.max=4",
        "nexus.ratelimit.forgot-ip.max=6",
        "nexus.ratelimit.forgot-identity.max=2",
        "nexus.ratelimit.reset-ip.max=3",
        "nexus.ratelimit.accept-invite-ip.max=3",
        "nexus.ratelimit.invite-create-actor.max=2",
        "nexus.ratelimit.invite-resend-actor.max=2"
})
class RateLimitLimitedIntegrationTest extends IntegrationTestBase {

    @Test
    void login_limitaPorIpEIdentidade_com429ERetryAfter_semRevelarConta() throws Exception {
        Tenant t = novoTenant("ATIVO");
        criarUsuario("rl.login@teste.local", Role.USER, t.getId());
        for (int i = 0; i < 4; i++) {
            assertThat(mvc.perform(post("/api/auth/login").contentType("application/json")
                    .content("{\"email\":\"rl.login@teste.local\",\"password\":\"errada-errada-1\"}").with(deIp("198.51.100.7")))
                    .andReturn().getResponse().getStatus()).isEqualTo(401);
        }
        MvcResult bloqueado = mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"email\":\"rl.login@teste.local\",\"password\":\"" + SENHA + "\"}").with(deIp("198.51.100.7"))).andReturn();
        assertThat(bloqueado.getResponse().getStatus()).isEqualTo(429);
        assertThat(bloqueado.getResponse().getHeader("Retry-After")).isNotNull().matches("\\d+");
        // conta inexistente sofre exatamente o mesmo tratamento (sem enumeração)
        for (int i = 0; i < 4; i++) {
            mvc.perform(post("/api/auth/login").contentType("application/json")
                    .content("{\"email\":\"rl.nao.existe@teste.local\",\"password\":\"errada-errada-1\"}").with(deIp("198.51.100.7")));
        }
        MvcResult fantasma = mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"email\":\"rl.nao.existe@teste.local\",\"password\":\"errada-errada-1\"}").with(deIp("198.51.100.7"))).andReturn();
        assertThat(fantasma.getResponse().getStatus()).isEqualTo(429);
        assertThat(corpoJson(fantasma).get("message")).isEqualTo(corpoJson(bloqueado).get("message"));
    }

    @Test
    void login_atacanteDeOutroIpNaoBloqueiaAVitima() throws Exception {
        Tenant t = novoTenant("ATIVO");
        criarUsuario("rl.vitima@teste.local", Role.USER, t.getId());
        for (int i = 0; i < 10; i++) {
            mvc.perform(post("/api/auth/login").contentType("application/json")
                    .content("{\"email\":\"rl.vitima@teste.local\",\"password\":\"errada-errada-1\"}").with(deIp("203.0.113.66")));
        }
        // a vítima, do IP dela, ainda entra (a chave combina IP+identidade)
        MvcResult ok = mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"email\":\"rl.vitima@teste.local\",\"password\":\"" + SENHA + "\"}").with(deIp("192.0.2.10"))).andReturn();
        assertThat(ok.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void forgot_limitePorIdentidadeESilencioso_respostaContinua202_eNaoGeraMaisEmails() throws Exception {
        Tenant t = novoTenant("ATIVO");
        criarUsuario("rl.forgot@teste.local", Role.USER, t.getId());
        MvcResult primeira = null;
        for (int i = 0; i < 4; i++) {
            MvcResult r = mvc.perform(post("/api/auth/forgot-password").contentType("application/json")
                    .content("{\"email\":\"rl.forgot@teste.local\"}").with(deIp("10.9.9." + (i + 1)))).andReturn();
            assertThat(r.getResponse().getStatus()).isEqualTo(202);
            if (primeira == null) {
                primeira = r;
            }
            assertThat(corpo(r)).isEqualTo(corpo(primeira));
        }
        assertThat(emails.sentTo("rl.forgot@teste.local")).hasSize(2);   // só 2 por identidade na janela
    }

    @Test
    void forgot_limitePorIpRetorna429_igualParaContaExistenteEInexistente() throws Exception {
        Tenant t = novoTenant("ATIVO");
        criarUsuario("rl.ip.existe@teste.local", Role.USER, t.getId());
        String[] alvos = {"rl.ip.existe@teste.local", "rl.ip.fantasma@teste.local"};
        for (int i = 0; i < 6; i++) {
            assertThat(mvc.perform(post("/api/auth/forgot-password").contentType("application/json")
                    .content("{\"email\":\"" + alvos[i % 2].replace("@", i + "@") + "\"}").with(deIp("172.16.0.5")))
                    .andReturn().getResponse().getStatus()).isEqualTo(202);
        }
        MvcResult a = mvc.perform(post("/api/auth/forgot-password").contentType("application/json")
                .content("{\"email\":\"rl.ip.existe@teste.local\"}").with(deIp("172.16.0.5"))).andReturn();
        MvcResult b = mvc.perform(post("/api/auth/forgot-password").contentType("application/json")
                .content("{\"email\":\"rl.ip.fantasma@teste.local\"}").with(deIp("172.16.0.5"))).andReturn();
        assertThat(a.getResponse().getStatus()).isEqualTo(429);
        assertThat(b.getResponse().getStatus()).isEqualTo(429);
        assertThat(corpoJson(a).get("message")).isEqualTo(corpoJson(b).get("message"));
        assertThat(a.getResponse().getHeader("Retry-After")).isNotNull();
    }

    @Test
    void reset_eAcceptInvite_limitadosPorIp_eTentativasDeAdivinharTokenFalham() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(mvc.perform(post("/api/auth/reset-password").contentType("application/json")
                    .content("{\"token\":\"" + com.designart.token.TokenCodec.generate() + "\",\"newPassword\":\"Senha-Longa-Valida-1\",\"confirmPassword\":\"Senha-Longa-Valida-1\"}")
                    .with(deIp("198.18.0.1"))).andReturn().getResponse().getStatus()).isEqualTo(400);
        }
        assertThat(mvc.perform(post("/api/auth/reset-password").contentType("application/json")
                .content("{\"token\":\"" + com.designart.token.TokenCodec.generate() + "\",\"newPassword\":\"Senha-Longa-Valida-1\",\"confirmPassword\":\"Senha-Longa-Valida-1\"}")
                .with(deIp("198.18.0.1"))).andReturn().getResponse().getStatus()).isEqualTo(429);
        // IP diferente não é afetado
        assertThat(mvc.perform(post("/api/auth/reset-password").contentType("application/json")
                .content("{\"token\":\"" + com.designart.token.TokenCodec.generate() + "\",\"newPassword\":\"Senha-Longa-Valida-1\",\"confirmPassword\":\"Senha-Longa-Valida-1\"}")
                .with(deIp("198.18.0.2"))).andReturn().getResponse().getStatus()).isEqualTo(400);

        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/auth/accept-invite").contentType("application/json")
                    .content("{\"token\":\"" + com.designart.token.TokenCodec.generate() + "\",\"newPassword\":\"Senha-Longa-Valida-1\",\"confirmPassword\":\"Senha-Longa-Valida-1\"}")
                    .with(deIp("198.18.0.3")));
        }
        assertThat(mvc.perform(post("/api/auth/accept-invite").contentType("application/json")
                .content("{\"token\":\"x\",\"newPassword\":\"Senha-Longa-Valida-1\",\"confirmPassword\":\"Senha-Longa-Valida-1\"}")
                .with(deIp("198.18.0.3"))).andReturn().getResponse().getStatus()).isEqualTo(429);
    }

    @Test
    void janelaLiberaComOClock() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/auth/reset-password").contentType("application/json")
                    .content("{\"token\":\"lixo\",\"newPassword\":\"Senha-Longa-Valida-1\",\"confirmPassword\":\"Senha-Longa-Valida-1\"}").with(deIp("198.19.0.1")));
        }
        assertThat(status429("198.19.0.1")).isTrue();
        clock.advance(Duration.ofHours(2));
        assertThat(status429("198.19.0.1")).isFalse();
    }

    private boolean status429(String ip) throws Exception {
        return mvc.perform(post("/api/auth/reset-password").contentType("application/json")
                .content("{\"token\":\"lixo\",\"newPassword\":\"Senha-Longa-Valida-1\",\"confirmPassword\":\"Senha-Longa-Valida-1\"}")
                .with(deIp(ip))).andReturn().getResponse().getStatus() == 429;
    }

    @Test
    void criacaoEReenvioDeConvite_limitadosPorAdministrador() throws Exception {
        Tenant t = novoTenant("ATIVO");
        criarUsuario("rl.admin@teste.local", Role.TENANT_ADMIN, t.getId());
        String admin = login("rl.admin@teste.local");
        assertThat(postJson("/api/usuarios", Map.of("email", "rl.c1@teste.local", "role", "USER"), admin).getResponse().getStatus()).isEqualTo(200);
        long id = corpoJson(postJson("/api/usuarios", Map.of("email", "rl.c2@teste.local", "role", "USER"), admin)).get("id").asLong();
        assertThat(postJson("/api/usuarios", Map.of("email", "rl.c3@teste.local", "role", "USER"), admin).getResponse().getStatus()).isEqualTo(429);
        assertThat(userRepository.findByEmail("rl.c3@teste.local")).isEmpty();

        assertThat(status(post("/api/usuarios/" + id + "/reenviar-convite"), admin)).isEqualTo(200);
        assertThat(status(post("/api/usuarios/" + id + "/reenviar-convite"), admin)).isEqualTo(200);
        assertThat(status(post("/api/usuarios/" + id + "/reenviar-convite"), admin)).isEqualTo(429);
        assertThat(List.of(emails.sentTo("rl.c2@teste.local").size())).containsExactly(3);
    }
}
