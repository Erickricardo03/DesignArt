package com.designart.ratelimit;

import com.designart.exception.TooManyRequestsException;
import com.designart.token.TokenCodec;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limitador CENTRAL, em memória, de janela deslizante, baseado em {@link Clock} (testável, sem
 * {@code System.currentTimeMillis()} espalhado). As chaves são guardadas como SHA-256 (nunca e-mail/IP
 * em claro na memória do limitador).
 * <p>
 * <b>Limitação conhecida (documentada):</b> o estado vive na JVM. Serve para UMA instância; com várias
 * instâncias ou após restart os contadores são independentes/zerados. Escalar horizontalmente exigirá um
 * armazenamento compartilhado (banco/Redis) — fora do escopo desta fase.
 */
@Service
public class RateLimitService {

    private static final int LIMPEZA_A_CADA = 2000;
    private static final int MAX_CHAVES = 100_000;

    private final Clock clock;
    private final Map<RateLimitPolicy, Limite> limites = new EnumMap<>(RateLimitPolicy.class);
    private final ConcurrentHashMap<String, Deque<Long>> janelas = new ConcurrentHashMap<>();
    private long operacoes;

    private record Limite(int max, long windowMillis) {
    }

    public RateLimitService(Clock clock, Environment env) {
        this.clock = clock;
        for (RateLimitPolicy p : RateLimitPolicy.values()) {
            int max = env.getProperty("nexus.ratelimit." + p.propertyName() + ".max", Integer.class, p.defaultMax());
            int window = env.getProperty("nexus.ratelimit." + p.propertyName() + ".window-seconds", Integer.class,
                    p.defaultWindowSeconds());
            limites.put(p, new Limite(max, window * 1000L));
        }
    }

    /**
     * Registra uma tentativa e informa se está DENTRO do limite. {@code false} = excedeu (a tentativa
     * excedente não é registrada).
     */
    public boolean tryAcquire(RateLimitPolicy policy, String key) {
        Limite limite = limites.get(policy);
        long agora = clock.millis();
        Deque<Long> janela = janelas.computeIfAbsent(policy.name() + ":" + TokenCodec.sha256Hex(key == null ? "" : key),
                k -> new ArrayDeque<>());
        boolean permitido;
        synchronized (janela) {
            while (!janela.isEmpty() && agora - janela.peekFirst() >= limite.windowMillis()) {
                janela.pollFirst();
            }
            permitido = janela.size() < limite.max();
            if (permitido) {
                janela.addLast(agora);
            }
        }
        limparSeNecessario(agora);
        return permitido;
    }

    /** Variante que lança {@link TooManyRequestsException} (429 genérico) ao exceder. */
    public void enforce(RateLimitPolicy policy, String key) {
        if (!tryAcquire(policy, key)) {
            throw new TooManyRequestsException(retryAfterSeconds(policy));
        }
    }

    public long retryAfterSeconds(RateLimitPolicy policy) {
        return Math.max(1, limites.get(policy).windowMillis() / 1000);
    }

    /** Zera todos os contadores (uso em testes). */
    public void reset() {
        janelas.clear();
    }

    private void limparSeNecessario(long agora) {
        boolean limpar;
        synchronized (this) {
            limpar = ++operacoes % LIMPEZA_A_CADA == 0 || janelas.size() > MAX_CHAVES;
        }
        if (!limpar) {
            return;
        }
        long maiorJanela = limites.values().stream().mapToLong(Limite::windowMillis).max().orElse(0);
        Iterator<Map.Entry<String, Deque<Long>>> it = janelas.entrySet().iterator();
        while (it.hasNext()) {
            Deque<Long> d = it.next().getValue();
            synchronized (d) {
                if (d.isEmpty() || agora - d.peekLast() >= maiorJanela) {
                    it.remove();
                }
            }
        }
    }
}
