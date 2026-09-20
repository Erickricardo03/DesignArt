package com.designart.observability;

import com.designart.observability.ObservabilityDtos.HealthDto;
import com.designart.security.SuperAdminOnly;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Saúde SANITIZADA da aplicação para o SUPER_ADMIN. Sem Spring Boot Actuator (nada de env, beans, configprops,
 * heapdump, threaddump ou segredos): só UP/DOWN dos componentes essenciais, nome da aplicação, versão do build (se
 * houver) e carimbo de tempo. Nenhuma URL, usuário ou detalhe de conexão é exposto.
 */
@RestController
@RequestMapping("/api/admin/system")
@SuperAdminOnly
@RequiredArgsConstructor
public class HealthController {

    private final DataSource dataSource;
    private final Clock clock;

    @Value("${spring.application.name:designart-api}")
    private String applicationName;

    @GetMapping("/health")
    public HealthDto health() {
        Map<String, String> components = new LinkedHashMap<>();
        components.put("application", "UP");
        components.put("database", databaseUp() ? "UP" : "DOWN");
        String overall = components.values().stream().allMatch("UP"::equals) ? "UP" : "DOWN";
        return new HealthDto(overall, LocalDateTime.now(clock), applicationName,
                HealthController.class.getPackage().getImplementationVersion(), components);
    }

    private boolean databaseUp() {
        try (var c = dataSource.getConnection()) {
            return c.isValid(2);
        } catch (Exception e) {
            return false;
        }
    }
}
