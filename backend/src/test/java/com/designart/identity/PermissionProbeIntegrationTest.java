package com.designart.identity;

import com.designart.model.Tenant;
import com.designart.security.Permission;
import com.designart.security.Role;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Ainda não há endpoint real de EQUIPE nem de SUPER_ADMIN (previstos para etapas
 * seguintes). Usa PermissionProbeController (ligado só aqui por test.probe.enabled), fora
 * do teste de cobertura; prova que as meta-anotações {@code @RequiresEquipe} e
 * {@code @SuperAdminOnly} funcionam com as authorities construídas do banco.
 */
@org.springframework.test.context.TestPropertySource(properties = "test.probe.enabled=true")
class PermissionProbeIntegrationTest extends IntegrationTestBase {
    @Test
    void equipeExigeAPermissaoEquipe_tenantAdminTemImplicita_superAdminNunca() throws Exception {
        Tenant t = novoTenant("ATIVO");
        criarUsuario("p.admin@teste.local", Role.TENANT_ADMIN, t.getId());
        criarUsuario("p.eq@teste.local", Role.USER, t.getId(), Permission.EQUIPE);
        criarUsuario("p.fin@teste.local", Role.USER, t.getId(), Permission.FINANCEIRO, Permission.CONFIGURACOES);
        criarUsuario("p.nada@teste.local", Role.USER, t.getId());
        criarUsuario("p.super@teste.local", Role.SUPER_ADMIN, null);

        assertThat(status(get("/api/_probe/equipe"), login("p.eq@teste.local"))).isEqualTo(200);
        assertThat(status(get("/api/_probe/equipe"), login("p.admin@teste.local"))).isEqualTo(200); // implícita
        assertThat(status(get("/api/_probe/equipe"), login("p.fin@teste.local"))).isEqualTo(403);
        assertThat(status(get("/api/_probe/equipe"), login("p.nada@teste.local"))).isEqualTo(403);
        assertThat(status(get("/api/_probe/equipe"), login("p.super@teste.local"))).isEqualTo(403); // SUPER_ADMIN sem PERM_*
        assertThat(status(get("/api/_probe/equipe"), null)).isEqualTo(401);
    }

    @Test
    void superAdminOnlyAceitaApenasSuperAdmin() throws Exception {
        Tenant t = novoTenant("ATIVO");
        criarUsuario("q.admin@teste.local", Role.TENANT_ADMIN, t.getId());
        criarUsuario("q.user@teste.local", Role.USER, t.getId(), Permission.EQUIPE);
        criarUsuario("q.super@teste.local", Role.SUPER_ADMIN, null);

        assertThat(status(get("/api/_probe/super"), login("q.super@teste.local"))).isEqualTo(200);
        assertThat(status(get("/api/_probe/super"), login("q.admin@teste.local"))).isEqualTo(403);
        assertThat(status(get("/api/_probe/super"), login("q.user@teste.local"))).isEqualTo(403);
        assertThat(status(get("/api/_probe/super"), null)).isEqualTo(401);
        // TENANT_ADMIN vs SUPER_ADMIN: cada um só no seu lado.
        assertThat(status(get("/api/_probe/tenant-admin"), login("q.admin@teste.local"))).isEqualTo(200);
        assertThat(status(get("/api/_probe/tenant-admin"), login("q.super@teste.local"))).isEqualTo(403);
    }
}
