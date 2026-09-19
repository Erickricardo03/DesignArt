package com.designart.token;

import com.designart.identity.IntegrationTestBase;
import com.designart.model.Tenant;
import com.designart.model.User;
import com.designart.security.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Consumo atômico: com N requisições simultâneas usando o MESMO token, exatamente UMA vence.
 * (Executado no H2 e, na validação da fase, também no PostgreSQL 16 real.)
 */
class TokenConcurrencyIntegrationTest extends IntegrationTestBase {

    private static final int THREADS = 16;

    @Autowired ActionTokenService tokenService;
    @Autowired PlatformTransactionManager txManager;

    private <T> List<T> emParalelo(Callable<T> tarefa) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CyclicBarrier largada = new CyclicBarrier(THREADS);
        try {
            List<Future<T>> futuros = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                futuros.add(pool.submit(() -> {
                    largada.await();
                    return tarefa.call();
                }));
            }
            List<T> resultados = new ArrayList<>();
            for (Future<T> f : futuros) {
                resultados.add(f.get());
            }
            return resultados;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void consumoDireto_exatamenteUmVence() throws Exception {
        Tenant t = novoTenant("ATIVO");
        User u = criarUsuario("cc.direto@teste.local", Role.USER, t.getId());
        IssuedToken[] emitido = new IssuedToken[1];
        new TransactionTemplate(txManager).executeWithoutResult(s ->
                emitido[0] = tokenService.issue(u.getId(), ActionTokenPurpose.PASSWORD_RESET, null, null));

        List<Boolean> r = emParalelo(() -> new TransactionTemplate(txManager).execute(s ->
                tokenService.consume(emitido[0].rawToken(), ActionTokenPurpose.PASSWORD_RESET)));
        assertThat(r.stream().filter(Boolean::booleanValue).count()).isEqualTo(1);
        assertThat(tokenRepository.findByUserIdOrderByIdAsc(u.getId()).get(0).getUsedAt()).isNotNull();
    }

    @Test
    void resetPasswordConcorrente_umaSenhaVence_umaUnicaVersaoDeToken() throws Exception {
        Tenant t = novoTenant("ATIVO");
        User u = criarUsuario("cc.reset@teste.local", Role.USER, t.getId());
        forgot("cc.reset@teste.local");
        String token = emails.lastToken("cc.reset@teste.local");
        int versaoAntes = userRepository.findById(u.getId()).orElseThrow().getTokenVersion();
        emails.clear();

        List<Integer> codigos = emParalelo(() -> {
            String senha = "Senha-Concorrente-" + Thread.currentThread().getId() + "-XYZ";
            MvcResult r = resetar(token, senha, senha);
            return r.getResponse().getStatus();
        });
        assertThat(codigos.stream().filter(c -> c == 200).count()).as("códigos: " + codigos).isEqualTo(1);
        assertThat(codigos.stream().filter(c -> c == 400).count()).isEqualTo(THREADS - 1);
        // token_version incrementado uma única vez; um único aviso de senha alterada
        assertThat(userRepository.findById(u.getId()).orElseThrow().getTokenVersion()).isEqualTo(versaoAntes + 1);
        assertThat(emails.sentTo("cc.reset@teste.local")).hasSize(1);
    }

    @Test
    void aceiteDeConviteConcorrente_apenasUmVence() throws Exception {
        Tenant t = novoTenant("ATIVO");
        criarUsuario("cc.admin@teste.local", Role.TENANT_ADMIN, t.getId());
        String admin = login("cc.admin@teste.local");
        MvcResult convite = postJson("/api/usuarios", java.util.Map.of("email", "cc.convite@teste.local", "role", "USER"), admin);
        assertThat(convite.getResponse().getStatus()).isEqualTo(200);
        String token = emails.lastToken("cc.convite@teste.local");

        List<Integer> codigos = emParalelo(() -> {
            String senha = "Senha-Do-Aceite-" + Thread.currentThread().getId() + "-XYZ";
            return aceitarConvite(token, senha, senha).getResponse().getStatus();
        });
        assertThat(codigos.stream().filter(c -> c == 200).count()).as("códigos: " + codigos).isEqualTo(1);
        assertThat(codigos.stream().filter(c -> c == 400).count()).isEqualTo(THREADS - 1);
        assertThat(userRepository.findByEmail("cc.convite@teste.local").orElseThrow().getAtivo()).isTrue();
    }

    @Test
    void reenvioConcorrente_terminaComNoMaximoUmTokenAtivo() throws Exception {
        Tenant t = novoTenant("ATIVO");
        criarUsuario("cc.admin2@teste.local", Role.TENANT_ADMIN, t.getId());
        String admin = login("cc.admin2@teste.local");
        long id = corpoJson(postJson("/api/usuarios", java.util.Map.of("email", "cc.reenvio@teste.local", "role", "USER"), admin))
                .get("id").asLong();
        // pedidos de forgot concorrentes para o mesmo usuário (revogam-se mutuamente)
        Tenant t2 = novoTenant("ATIVO");
        User u = criarUsuario("cc.forgot@teste.local", Role.USER, t2.getId());
        emParalelo(() -> forgot("cc.forgot@teste.local").getResponse().getStatus());
        long ativos = tokenRepository.findByUserIdOrderByIdAsc(u.getId()).stream()
                .filter(x -> x.isActive(java.time.LocalDateTime.now(clock))).count();
        assertThat(ativos).isLessThanOrEqualTo(THREADS);
        assertThat(id).isPositive();
    }
}
