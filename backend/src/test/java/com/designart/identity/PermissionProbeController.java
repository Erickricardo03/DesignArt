package com.designart.identity;

import com.designart.security.RequiresEquipe;
import com.designart.security.SuperAdminOnly;
import com.designart.security.TenantAdminOnly;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sonda EXCLUSIVA de testes: ainda não há endpoint real de EQUIPE nem de
 * SUPER_ADMIN. Só existe quando {@code test.probe.enabled=true} (ligado apenas em
 * PermissionProbeIntegrationTest), então NÃO aparece nos demais testes nem no
 * teste de cobertura de endpoints.
 */
@RestController
@ConditionalOnProperty(name = "test.probe.enabled", havingValue = "true")
@RequestMapping("/api/_probe")
public class PermissionProbeController {

    @GetMapping("/equipe")
    @RequiresEquipe
    public String equipe() {
        return "ok";
    }

    @GetMapping("/tenant-admin")
    @TenantAdminOnly
    public String tenantAdmin() {
        return "ok";
    }

    @GetMapping("/super")
    @SuperAdminOnly
    public String superAdmin() {
        return "ok";
    }
}
