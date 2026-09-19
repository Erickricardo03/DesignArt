package com.designart.ratelimit;

import com.designart.exception.TooManyRequestsException;
import com.designart.support.MutableClock;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Limitador central: janela deslizante, baseado em Clock, sem estado global escondido. */
class RateLimitServiceTest {

    private final MutableClock clock = new MutableClock();

    private RateLimitService servico(String... props) {
        MockEnvironment env = new MockEnvironment();
        for (int i = 0; i < props.length; i += 2) {
            env.setProperty(props[i], props[i + 1]);
        }
        return new RateLimitService(clock, env);
    }

    @Test
    void permiteAteOLimiteEBloqueiaDepois() {
        RateLimitService rl = servico("nexus.ratelimit.reset-ip.max", "3");
        assertThat(rl.tryAcquire(RateLimitPolicy.RESET_IP, "1.1.1.1")).isTrue();
        assertThat(rl.tryAcquire(RateLimitPolicy.RESET_IP, "1.1.1.1")).isTrue();
        assertThat(rl.tryAcquire(RateLimitPolicy.RESET_IP, "1.1.1.1")).isTrue();
        assertThat(rl.tryAcquire(RateLimitPolicy.RESET_IP, "1.1.1.1")).isFalse();
        assertThat(rl.tryAcquire(RateLimitPolicy.RESET_IP, "1.1.1.1")).isFalse();
    }

    @Test
    void chavesEPoliticasSaoIndependentes() {
        RateLimitService rl = servico("nexus.ratelimit.reset-ip.max", "1", "nexus.ratelimit.accept-invite-ip.max", "1");
        assertThat(rl.tryAcquire(RateLimitPolicy.RESET_IP, "1.1.1.1")).isTrue();
        assertThat(rl.tryAcquire(RateLimitPolicy.RESET_IP, "1.1.1.1")).isFalse();
        assertThat(rl.tryAcquire(RateLimitPolicy.RESET_IP, "2.2.2.2")).isTrue();           // outro IP
        assertThat(rl.tryAcquire(RateLimitPolicy.ACCEPT_INVITE_IP, "1.1.1.1")).isTrue();   // outra política
    }

    @Test
    void janelaResetaComOClock() {
        RateLimitService rl = servico("nexus.ratelimit.reset-ip.max", "2", "nexus.ratelimit.reset-ip.window-seconds", "60");
        assertThat(rl.tryAcquire(RateLimitPolicy.RESET_IP, "k")).isTrue();
        assertThat(rl.tryAcquire(RateLimitPolicy.RESET_IP, "k")).isTrue();
        assertThat(rl.tryAcquire(RateLimitPolicy.RESET_IP, "k")).isFalse();
        clock.advance(Duration.ofSeconds(59));
        assertThat(rl.tryAcquire(RateLimitPolicy.RESET_IP, "k")).isFalse();  // ainda dentro da janela
        clock.advance(Duration.ofSeconds(2));
        assertThat(rl.tryAcquire(RateLimitPolicy.RESET_IP, "k")).isTrue();   // janela expirou
        clock.reset();
    }

    @Test
    void janelaDeslizanteLiberaUmaVagaPorVez() {
        RateLimitService rl = servico("nexus.ratelimit.reset-ip.max", "2", "nexus.ratelimit.reset-ip.window-seconds", "100");
        assertThat(rl.tryAcquire(RateLimitPolicy.RESET_IP, "k")).isTrue();      // t=0
        clock.advance(Duration.ofSeconds(60));
        assertThat(rl.tryAcquire(RateLimitPolicy.RESET_IP, "k")).isTrue();      // t=60
        assertThat(rl.tryAcquire(RateLimitPolicy.RESET_IP, "k")).isFalse();
        clock.advance(Duration.ofSeconds(41));                                  // t=101: a de t=0 saiu
        assertThat(rl.tryAcquire(RateLimitPolicy.RESET_IP, "k")).isTrue();
        assertThat(rl.tryAcquire(RateLimitPolicy.RESET_IP, "k")).isFalse();     // a de t=60 ainda conta
        clock.reset();
    }

    @Test
    void enforceLancaTooManyRequestsComRetryAfter() {
        RateLimitService rl = servico("nexus.ratelimit.forgot-ip.max", "1", "nexus.ratelimit.forgot-ip.window-seconds", "120");
        rl.enforce(RateLimitPolicy.FORGOT_IP, "x");
        assertThatThrownBy(() -> rl.enforce(RateLimitPolicy.FORGOT_IP, "x"))
                .isInstanceOfSatisfying(TooManyRequestsException.class, e -> {
                    assertThat(e.getRetryAfterSeconds()).isEqualTo(120);
                    assertThat(e.getMessage()).doesNotContain("x").doesNotContain("conta").doesNotContain("e-mail");
                });
    }

    @Test
    void tentativaExcedenteNaoEstendeOBloqueio() {
        RateLimitService rl = servico("nexus.ratelimit.reset-ip.max", "1", "nexus.ratelimit.reset-ip.window-seconds", "60");
        assertThat(rl.tryAcquire(RateLimitPolicy.RESET_IP, "k")).isTrue();
        for (int i = 0; i < 50; i++) {
            clock.advance(Duration.ofSeconds(1));
            assertThat(rl.tryAcquire(RateLimitPolicy.RESET_IP, "k")).isFalse(); // tentativas negadas não são registradas
        }
        clock.advance(Duration.ofSeconds(11));                                    // 61s desde a única registrada
        assertThat(rl.tryAcquire(RateLimitPolicy.RESET_IP, "k")).isTrue();
        clock.reset();
    }

    @Test
    void padroesSaoModeradosEConfiguraveis() {
        RateLimitService padrao = servico();
        // padrão de forgot por identidade: 3 por hora
        for (int i = 0; i < 3; i++) {
            assertThat(padrao.tryAcquire(RateLimitPolicy.FORGOT_IDENTITY, "a@b.co")).isTrue();
        }
        assertThat(padrao.tryAcquire(RateLimitPolicy.FORGOT_IDENTITY, "a@b.co")).isFalse();
        assertThat(padrao.retryAfterSeconds(RateLimitPolicy.FORGOT_IDENTITY)).isEqualTo(3600);
        // política por IP+identidade no login: 10 por 15 min
        assertThat(RateLimitPolicy.LOGIN_IP_IDENTITY.defaultMax()).isEqualTo(10);
        assertThat(RateLimitPolicy.LOGIN_IP_IDENTITY.defaultWindowSeconds()).isEqualTo(900);
    }

    @Test
    void resetLimpaTodosOsContadores() {
        RateLimitService rl = servico("nexus.ratelimit.reset-ip.max", "1");
        assertThat(rl.tryAcquire(RateLimitPolicy.RESET_IP, "k")).isTrue();
        assertThat(rl.tryAcquire(RateLimitPolicy.RESET_IP, "k")).isFalse();
        rl.reset();
        assertThat(rl.tryAcquire(RateLimitPolicy.RESET_IP, "k")).isTrue();
    }
}
