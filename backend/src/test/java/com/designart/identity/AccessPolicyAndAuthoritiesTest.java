package com.designart.identity;

import com.designart.config.AccessPolicy;
import com.designart.model.Tenant;
import com.designart.model.User;
import com.designart.repository.TenantRepository;
import com.designart.security.AuthoritiesFactory;
import com.designart.security.Permission;
import com.designart.security.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.security.core.GrantedAuthority;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unitários da regra de acesso (sem banco): a regra SUPER_ADMIN <=> tenant_id
 * NULL e o status do tenant, mais a construção de authorities. Estes testes
 * exercitam também estados que o banco/entidade já impedem de existir
 * (defesa em profundidade contra dados legados).
 */
class AccessPolicyAndAuthoritiesTest {

    private final TenantRepository tenants = Mockito.mock(TenantRepository.class);
    private final AccessPolicy policy = new AccessPolicy(tenants);

    private void tenantComStatus(long id, String status) {
        Mockito.when(tenants.findById(id)).thenReturn(Optional.of(Tenant.builder().id(id).status(status).build()));
    }

    private User user(Role role, Long tenantId, Boolean ativo) {
        return User.builder().id(1L).email("x@teste.local").role(role).tenantId(tenantId).ativo(ativo).build();
    }

    @Test
    void usuarioNuloEInativoOuSemRoleSaoNegados() {
        assertThat(policy.podeOperar(null)).isFalse();
        assertThat(policy.podeOperar(user(Role.SUPER_ADMIN, null, false))).isFalse();
        assertThat(policy.podeOperar(user(Role.SUPER_ADMIN, null, null))).isFalse();
        assertThat(policy.podeOperar(user(null, null, true))).isFalse();
    }

    @Test
    void superAdminSemTenantOpera_semConsultarTenant() {
        assertThat(policy.podeOperar(user(Role.SUPER_ADMIN, null, true))).isTrue();
        Mockito.verifyNoInteractions(tenants);
    }

    @Test
    void superAdminComTenantENegado() {
        tenantComStatus(7, "ATIVO");
        assertThat(policy.podeOperar(user(Role.SUPER_ADMIN, 7L, true))).isFalse();
    }

    @Test
    void usuarioComumComTenantNuloENegado_tenantNuloNaoViraSuperAdmin() {
        assertThat(policy.podeOperar(user(Role.USER, null, true))).isFalse();
        assertThat(policy.podeOperar(user(Role.TENANT_ADMIN, null, true))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER", "TENANT_ADMIN"})
    void usuarioDeTenantOperaSoComTenantAtivo(String roleName) {
        Role role = Role.valueOf(roleName);
        tenantComStatus(1, "ATIVO");
        tenantComStatus(2, "SUSPENSO");
        tenantComStatus(3, "INATIVO");
        assertThat(policy.podeOperar(user(role, 1L, true))).isTrue();
        assertThat(policy.podeOperar(user(role, 2L, true))).isFalse();
        assertThat(policy.podeOperar(user(role, 3L, true))).isFalse();
        assertThat(policy.podeOperar(user(role, 99L, true))).isFalse(); // tenant inexistente
        assertThat(policy.podeOperar(user(role, 1L, false))).isFalse(); // inativo
    }

    // ---------------- authorities ----------------

    private Set<String> nomes(User u) {
        return AuthoritiesFactory.from(u).stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }

    @Test
    void tenantAdminTemTodasAsPermissoesImplicitamente() {
        User admin = user(Role.TENANT_ADMIN, 1L, true); // nenhuma permissão gravada
        assertThat(nomes(admin)).containsExactlyInAnyOrder("ROLE_TENANT_ADMIN",
                "PERM_FINANCEIRO", "PERM_EQUIPE", "PERM_CONFIGURACOES");
    }

    @Test
    void userSoTemAsPermissoesExplicitas() {
        User u = user(Role.USER, 1L, true);
        assertThat(nomes(u)).containsExactly("ROLE_USER");
        u.setPermissoes(Set.of(Permission.EQUIPE));
        assertThat(nomes(u)).containsExactlyInAnyOrder("ROLE_USER", "PERM_EQUIPE");
        u.setPermissoes(Set.of(Permission.FINANCEIRO, Permission.CONFIGURACOES));
        assertThat(nomes(u)).containsExactlyInAnyOrder("ROLE_USER", "PERM_FINANCEIRO", "PERM_CONFIGURACOES");
    }

    @Test
    void superAdminNuncaRecebePermissoesDeTenant_mesmoComPermissoesGravadas() {
        User s = user(Role.SUPER_ADMIN, null, true);
        s.setPermissoes(Set.of(Permission.FINANCEIRO, Permission.EQUIPE, Permission.CONFIGURACOES));
        assertThat(nomes(s)).containsExactly("ROLE_SUPER_ADMIN");
    }

    @Test
    void semRoleNaoHaNenhumaAuthority() {
        List<GrantedAuthority> auths = AuthoritiesFactory.from(user(null, 1L, true));
        assertThat(auths).isEmpty();
    }
}
