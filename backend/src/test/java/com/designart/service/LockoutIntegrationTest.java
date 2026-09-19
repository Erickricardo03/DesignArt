package com.designart.service;

import com.designart.audit.AuditAction;
import com.designart.audit.AuditEvent;
import com.designart.identity.IntegrationTestBase;
import com.designart.model.Tenant;
import com.designart.model.User;
import com.designart.security.Role;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Lockout moderado: 5 falhas => 5/10/20/30 min, resposta genérica, sem DoS trivial. */
class LockoutIntegrationTest extends IntegrationTestBase {

    static final String ERRADA = "senha-errada-xyz-999";

    @Test
    void duracaoDoBloqueio_progressivaComTeto() {
        assertThat(LoginAttemptService.duracaoDoBloqueio(5)).isEqualTo(5);
        assertThat(LoginAttemptService.duracaoDoBloqueio(10)).isEqualTo(10);
        assertThat(LoginAttemptService.duracaoDoBloqueio(15)).isEqualTo(20);
        assertThat(LoginAttemptService.duracaoDoBloqueio(20)).isEqualTo(30);
        assertThat(LoginAttemptService.duracaoDoBloqueio(500)).isEqualTo(30);
        assertThat(LoginAttemptService.duracaoDoBloqueio(Integer.MAX_VALUE)).isEqualTo(30);
    }

    private void falhar(String email, int vezes) throws Exception {
        for (int i = 0; i < vezes; i++) {
            assertThat(tentarLogin(email, ERRADA).getResponse().getStatus()).isEqualTo(401);
        }
    }

    @Test
    void quatroFalhasNaoBloqueiam_quintaBloqueia_mesmoComSenhaCorreta() throws Exception {
        Tenant t = novoTenant("ATIVO");
        User u = criarUsuario("lk.basico@teste.local", Role.USER, t.getId());
        falhar("lk.basico@teste.local", 4);
        assertThat(userRepository.findById(u.getId()).orElseThrow().getLockedUntil()).isNull();
        // sucesso antes do limiar zera o contador
        assertThat(tentarLogin("lk.basico@teste.local", SENHA).getResponse().getStatus()).isEqualTo(200);
        assertThat(userRepository.findById(u.getId()).orElseThrow().getFailedLogins()).isZero();

        falhar("lk.basico@teste.local", 5);
        assertThat(userRepository.findById(u.getId()).orElseThrow().getLockedUntil()).isNotNull();
        // bloqueada: até a senha CORRETA recebe a mesma resposta genérica
        MvcResult bloqueado = tentarLogin("lk.basico@teste.local", SENHA);
        MvcResult errada = tentarLogin("lk.inexistente@teste.local", ERRADA);
        assertThat(bloqueado.getResponse().getStatus()).isEqualTo(401);
        assertThat(corpoJson(bloqueado).get("message").asText()).isEqualTo(corpoJson(errada).get("message").asText());
        assertThat(corpo(bloqueado)).doesNotContainIgnoringCase("bloque").doesNotContainIgnoringCase("lock").doesNotContain("token");
    }

    @Test
    void desbloqueiaSozinhoAposOTempo_eProgrideNasReincidencias() throws Exception {
        Tenant t = novoTenant("ATIVO");
        User u = criarUsuario("lk.tempo@teste.local", Role.USER, t.getId());
        falhar("lk.tempo@teste.local", 5);                              // 5 min
        clock.advance(Duration.ofMinutes(4));
        assertThat(tentarLogin("lk.tempo@teste.local", SENHA).getResponse().getStatus()).isEqualTo(401);
        clock.advance(Duration.ofMinutes(2));
        // agora a janela passou; o contador segue em 5, então a próxima falha (6) não bloqueia, mas a 10ª sim (10 min)
        falhar("lk.tempo@teste.local", 4);
        User meio = userRepository.findById(u.getId()).orElseThrow();
        assertThat(meio.getFailedLogins()).isEqualTo(9);
        falhar("lk.tempo@teste.local", 1);
        User depois = userRepository.findById(u.getId()).orElseThrow();
        assertThat(Duration.between(java.time.LocalDateTime.now(clock), depois.getLockedUntil()).toMinutes()).isBetween(9L, 10L);
        clock.advance(Duration.ofMinutes(11));
        assertThat(tentarLogin("lk.tempo@teste.local", SENHA).getResponse().getStatus()).isEqualTo(200);
        assertThat(userRepository.findById(u.getId()).orElseThrow().getFailedLogins()).isZero();
    }

    @Test
    void contaInexistenteEInativaNaoAcumulamContador_eNaoCriamLinhas() throws Exception {
        Tenant t = novoTenant("ATIVO");
        User inativo = criarUsuario("lk.inativo@teste.local", Role.USER, t.getId(), false, SENHA);
        long usuarios = userRepository.count();
        for (int i = 0; i < 8; i++) {
            tentarLogin("lk.fantasma@teste.local", ERRADA);
            tentarLogin("lk.inativo@teste.local", SENHA);
        }
        assertThat(userRepository.count()).isEqualTo(usuarios);
        assertThat(userRepository.findById(inativo.getId()).orElseThrow().getFailedLogins()).isZero();
    }

    @Test
    void bloqueioEAuditadoUmaVez_semSegredos() throws Exception {
        Tenant t = novoTenant("ATIVO");
        User u = criarUsuario("lk.audit@teste.local", Role.USER, t.getId());
        long antes = maxAuditId();
        falhar("lk.audit@teste.local", 5);
        List<AuditEvent> bloqueios = auditRepository.findAllByOrderByIdAsc().stream()
                .filter(e -> e.getId() > antes && e.getAction() == AuditAction.ACCOUNT_LOCKED).toList();
        assertThat(bloqueios).hasSize(1);
        assertThat(bloqueios.get(0).getTargetUserId()).isEqualTo(u.getId());
        assertThat(dumpAuditoria()).doesNotContain(ERRADA).doesNotContain(SENHA);
    }

    @Test
    void redefinirSenhaDesbloqueiaAConta() throws Exception {
        Tenant t = novoTenant("ATIVO");
        User u = criarUsuario("lk.reset@teste.local", Role.USER, t.getId());
        falhar("lk.reset@teste.local", 5);
        assertThat(userRepository.findById(u.getId()).orElseThrow().getLockedUntil()).isNotNull();
        forgot("lk.reset@teste.local");
        String token = emails.lastToken("lk.reset@teste.local");
        String nova = "Senha-Pos-Bloqueio-2026";
        assertThat(resetar(token, nova, nova).getResponse().getStatus()).isEqualTo(200);
        User depois = userRepository.findById(u.getId()).orElseThrow();
        assertThat(depois.getLockedUntil()).isNull();
        assertThat(depois.getFailedLogins()).isZero();
        assertThat(tentarLogin("lk.reset@teste.local", nova).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void bloqueioNaoImpedeForgotPassword() throws Exception {
        Tenant t = novoTenant("ATIVO");
        criarUsuario("lk.forgot@teste.local", Role.USER, t.getId());
        falhar("lk.forgot@teste.local", 5);
        assertThat(forgot("lk.forgot@teste.local").getResponse().getStatus()).isEqualTo(202);
        assertThat(emails.lastToken("lk.forgot@teste.local")).isNotNull();
    }
}
