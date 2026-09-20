package com.designart.observability;

import com.designart.exception.InvalidRequestException;
import com.designart.exception.ServiceUnavailableException;
import com.designart.security.AuthenticatedAny;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SOMENTE TESTES: endpoints que falham de propósito para exercitar a captura de erros. As mensagens das exceções
 * carregam "segredos" falsos para provar que nada disso é armazenado.
 */
@RestController
@RequestMapping("/api/test-observability")
@AuthenticatedAny
public class ObservabilityProbeController {

    public static final String FAKE_SECRET = "segredo-falso-xyz-987 password=hunter2 Bearer abcdefghijklmnop";

    @GetMapping("/boom/{id}")
    public String boom(@PathVariable Long id) {
        throw new IllegalStateException(FAKE_SECRET + " id=" + id);
    }

    @GetMapping("/db")
    public String db() {
        throw new DataIntegrityViolationException("insert into users values ('" + FAKE_SECRET + "')");
    }

    @GetMapping("/unavailable")
    public String unavailable() {
        throw new ServiceUnavailableException("Servico de e-mail indisponivel " + FAKE_SECRET);
    }

    @GetMapping("/bad")
    public String bad() {
        throw new InvalidRequestException("entrada invalida " + FAKE_SECRET);
    }
}
