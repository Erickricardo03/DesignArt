package com.designart.audit;

import com.designart.identity.IntegrationTestBase;
import com.designart.model.Tenant;
import com.designart.model.User;
import com.designart.security.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.IllegalTransactionStateException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Semântica transacional da auditoria:
 * <ul>
 *   <li>operação + evento de sucesso: MESMA transação (atômicos);</li>
 *   <li>evento de falha (LOGIN_FAILURE): transação independente que nunca derruba a resposta.</li>
 * </ul>
 * A falha da gravação é simulada com um spy no {@link ClientIpResolver}, chamado
 * DENTRO da gravação do evento.
 */
class AuditTransactionIntegrationTest extends IntegrationTestBase {

    @Autowired AuditService auditService;
    @SpyBean ClientIpResolver ipResolver;

    @AfterEach
    void restaurar() {
        Mockito.reset(ipResolver);
    }

    private void auditoriaFalha() {
        Mockito.doThrow(new IllegalStateException("falha simulada na gravação da auditoria"))
                .when(ipResolver).resolveForAudit(any());
    }

    @Test
    void successExigeTransacaoAberta_naoHaAuditoriaSemOperacaoTransacional() {
        assertThatThrownBy(() -> auditService.success(AuditAction.LOGIN_SUCCESS, AuditActor.system(),
                AuditTarget.none(), AuditMetadata.EMPTY))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void seAAuditoriaFalhaAOperacaoDeNegocioEDesfeita_criacao() throws Exception {
        Tenant t = novoTenant("ATIVO");
        criarUsuario("tx.admin.cria@teste.local", Role.TENANT_ADMIN, t.getId());
        String token = login("tx.admin.cria@teste.local");

        long tokensAntes = tokenRepository.count();
        auditoriaFalha();
        assertThatThrownBy(() -> mvc.perform(post("/api/usuarios").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token)
                .content(json.writeValueAsString(Map.of("email", "tx.nao.deve.existir@teste.local", "role", "USER")))))
                .hasStackTraceContaining("falha simulada");

        // Operação NÃO concluída sem auditoria: o usuário não foi criado, nenhum token restou e nada foi enviado.
        assertThat(userRepository.findByEmail("tx.nao.deve.existir@teste.local")).isEmpty();
        assertThat(tokenRepository.count()).isEqualTo(tokensAntes);
        assertThat(emails.sentTo("tx.nao.deve.existir@teste.local")).isEmpty();
    }

    @Test
    void seAAuditoriaFalhaAOperacaoDeNegocioEDesfeita_mudancaDeRole() throws Exception {
        Tenant t = novoTenant("ATIVO");
        criarUsuario("tx.admin.role@teste.local", Role.TENANT_ADMIN, t.getId());
        User alvo = criarUsuario("tx.alvo.role@teste.local", Role.USER, t.getId());
        String token = login("tx.admin.role@teste.local");
        int versaoAntes = userRepository.findById(alvo.getId()).orElseThrow().getTokenVersion();

        auditoriaFalha();
        assertThatThrownBy(() -> mvc.perform(put("/api/usuarios/" + alvo.getId()).contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token).content("{\"role\":\"TENANT_ADMIN\"}")))
                .hasStackTraceContaining("falha simulada");

        User depois = userRepository.findById(alvo.getId()).orElseThrow();
        assertThat(depois.getRole()).isEqualTo(Role.USER);            // mudança de role desfeita
        assertThat(depois.getTokenVersion()).isEqualTo(versaoAntes);  // e a revogação de sessões também
    }

    @Test
    void loginSuccessSemAuditoriaNaoConcedeSessao_falhaFechada() throws Exception {
        Tenant t = novoTenant("ATIVO");
        User u = criarUsuario("tx.login.ok@teste.local", Role.USER, t.getId());

        auditoriaFalha();
        assertThatThrownBy(() -> tentarLogin("tx.login.ok@teste.local", SENHA)).hasStackTraceContaining("falha simulada");
        assertThat(userRepository.findById(u.getId()).orElseThrow().getLastLoginAt()).isNull(); // login desfeito
    }

    @Test
    void loginFailureNaoDependeDaTransacaoDoLogin_eErroDeGravacaoNaoVira500() throws Exception {
        Tenant t = novoTenant("ATIVO");
        criarUsuario("tx.login.falha@teste.local", Role.USER, t.getId());

        // (a) funcionamento normal: o evento persiste mesmo com o rollback da transação do login.
        long antes = maxAuditId();
        assertThat(tentarLogin("tx.login.falha@teste.local", "senha-errada-999").getResponse().getStatus()).isEqualTo(401);
        assertThat(maxAuditId()).isGreaterThan(antes);

        // (b) se a gravação do evento falhar, a resposta continua sendo o 401 genérico (nunca 500).
        auditoriaFalha();
        long antesFalha = maxAuditId();
        MvcResult r = tentarLogin("tx.login.falha@teste.local", "senha-errada-999");
        assertThat(r.getResponse().getStatus()).isEqualTo(401);
        assertThat(corpoJson(r).get("message").asText()).isEqualTo("Usuário ou senha inválidos.");
        assertThat(maxAuditId()).isEqualTo(antesFalha); // nada foi gravado, mas nada quebrou
    }
}
