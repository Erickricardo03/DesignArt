package com.designart.admin;

import com.designart.model.Tenant;
import com.designart.model.User;
import com.designart.security.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** /api/admin/** é exclusivo da role SUPER_ADMIN e não dá ao SUPER_ADMIN acesso aos dados de negócio. */
class AdminAccessIntegrationTest extends AdminTestBase {

    private static MockHttpServletRequestBuilder comCorpo(MockHttpServletRequestBuilder b) {
        return b.contentType(MediaType.APPLICATION_JSON).content("{}");
    }

    private static final List<Supplier<MockHttpServletRequestBuilder>> ROTAS_ADMIN = List.of(
            () -> get("/api/admin/tenants"),
            () -> get("/api/admin/tenants/1"),
            () -> comCorpo(post("/api/admin/tenants")),
            () -> comCorpo(post("/api/admin/tenants/1/suspend")),
            () -> comCorpo(post("/api/admin/tenants/1/reactivate")),
            () -> comCorpo(post("/api/admin/tenants/1/users/1/resend-invite")),
            () -> get("/api/admin/tenants/1/subscription"),
            () -> comCorpo(put("/api/admin/tenants/1/subscription")),
            () -> get("/api/admin/tenants/1/entitlements"),
            () -> get("/api/admin/tenants/1/feature-overrides"),
            () -> comCorpo(put("/api/admin/tenants/1/feature-overrides/FINANCEIRO")),
            () -> delete("/api/admin/tenants/1/feature-overrides/FINANCEIRO"),
            () -> get("/api/admin/tenants/1/branding"),
            () -> comCorpo(put("/api/admin/tenants/1/branding")),
            () -> delete("/api/admin/tenants/1/branding"),
            () -> get("/api/admin/plans"),
            () -> get("/api/admin/plans/1"),
            () -> comCorpo(post("/api/admin/plans")),
            () -> comCorpo(put("/api/admin/plans/1")),
            () -> get("/api/admin/plans/1/features"),
            () -> comCorpo(put("/api/admin/plans/1/features")),
            () -> get("/api/admin/features"));

    @Test
    void anonimoRecebe401EmTodasAsRotasAdmin() throws Exception {
        for (var rota : ROTAS_ADMIN) {
            assertThat(status(rota.get(), null)).as(rota.get().toString()).isEqualTo(401);
        }
    }

    @Test
    void tenantAdminEUserRecebem403EmTodasAsRotasAdmin() throws Exception {
        Tenant t = novoTenant("ATIVO");
        criarUsuario("ac.tadmin@teste.local", Role.TENANT_ADMIN, t.getId());
        criarUsuario("ac.user@teste.local", Role.USER, t.getId());
        String admin = login("ac.tadmin@teste.local");
        String user = login("ac.user@teste.local");
        for (var rota : ROTAS_ADMIN) {
            assertThat(status(rota.get(), admin)).as("TENANT_ADMIN " + rota.get()).isEqualTo(403);
            assertThat(status(rota.get(), user)).as("USER " + rota.get()).isEqualTo(403);
        }
        // nem o próprio tenant do administrador é acessível por aqui
        assertThat(status(get("/api/admin/tenants/" + t.getId()), admin)).isEqualTo(403);
    }

    @Test
    void superAdminAcessaAsRotasGlobais() throws Exception {
        String sup = superJwt();
        assertThat(status(get("/api/admin/tenants"), sup)).isEqualTo(200);
        assertThat(status(get("/api/admin/plans"), sup)).isEqualTo(200);
        assertThat(status(get("/api/admin/features"), sup)).isEqualTo(200);
    }

    @Test
    void superAdminContinuaBloqueadoNosEndpointsDeNegocioDosTenants() throws Exception {
        String sup = superJwt();
        for (String rota : List.of("/api/clientes", "/api/tarefas", "/api/roteiros", "/api/eventos", "/api/despesas",
                "/api/usuarios", "/api/dashboard/stats", "/api/relatorios/mensal")) {
            int s = status(get(rota), sup);
            assertThat(s).as("SUPER_ADMIN em " + rota).isIn(403, 404);
            assertThat(s).isNotEqualTo(200);
        }
    }

    @Autowired
    com.designart.config.AccessPolicy accessPolicy;

    @Test
    void tenantIdNuloSemRoleSuperAdminNaoConcedeAcesso() throws Exception {
        Tenant t = novoTenant("ATIVO");
        criarUsuario("ac.semtenant@teste.local", Role.TENANT_ADMIN, t.getId());
        String jwt = login("ac.semtenant@teste.local");
        assertThat(status(get("/api/admin/plans"), jwt)).isEqualTo(403);
        // O banco (CHECK users_role_tenant_chk) já impede persistir tenant_id NULL fora do SUPER_ADMIN.
        assertThat(org.assertj.core.api.Assertions.catchThrowable(
                () -> jdbc.update("update users set tenant_id = null where email = ?", "ac.semtenant@teste.local")))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        // E, mesmo em memória, nenhuma conta sem tenant que não seja SUPER_ADMIN opera; SUPER_ADMIN com tenant também não.
        for (Role r : List.of(Role.USER, Role.TENANT_ADMIN)) {
            assertThat(accessPolicy.podeOperar(User.builder().email("x@teste.local").role(r).tenantId(null).ativo(true).build())).as(r + " sem tenant").isFalse();
        }
        assertThat(accessPolicy.podeOperar(User.builder().email("y@teste.local").role(Role.SUPER_ADMIN).tenantId(t.getId()).ativo(true).build())).isFalse();
        assertThat(accessPolicy.podeOperar(User.builder().email("z@teste.local").role(Role.SUPER_ADMIN).tenantId(null).ativo(true).build())).isTrue();
    }

    @Test
    void superAdminComTenantOuInativoNaoAcessa() throws Exception {
        String sup = superJwt();
        assertThat(status(get("/api/admin/plans"), sup)).isEqualTo(200);
        String email = "adm.super.inativo@teste.local";
        User u = criarUsuario(email, Role.SUPER_ADMIN, null);
        String jwt = login(email);
        jdbc.update("update users set ativo = false where id = ?", u.getId());
        assertThat(status(get("/api/admin/plans"), jwt)).isEqualTo(401);
    }

    @Test
    void tokenDeTenantAdminNaoPodeSerReaproveitadoAposPromoverRoleForaDoBanco() throws Exception {
        // role vem SEMPRE do banco, não do JWT: rebaixar um SUPER_ADMIN derruba o acesso admin imediatamente.
        String email = "super.rebaixado@teste.local";
        User u = criarUsuario(email, Role.SUPER_ADMIN, null);
        String jwt = login(email);
        assertThat(status(get("/api/admin/plans"), jwt)).isEqualTo(200);
        Tenant t = novoTenant("ATIVO");
        jdbc.update("update users set role = 'USER', tenant_id = ? where id = ?", t.getId(), u.getId());
        assertThat(status(get("/api/admin/plans"), jwt)).isEqualTo(403);
    }
}
