package com.designart.audit;

import com.designart.security.Role;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Guardas ESTRUTURAIS: falham se alguém, no futuro, abrir uma porta para texto
 * livre/segredos ou para alterar/remover eventos.
 */
class AuditStructureGuardTest {

    @Test
    void eventoNaoTemConstrutorNemFabricaPublicosNemSetters() {
        for (Constructor<?> c : AuditEvent.class.getDeclaredConstructors()) {
            assertThat(Modifier.isPublic(c.getModifiers())).as("construtor público: " + c).isFalse();
        }
        for (Method m : AuditEvent.class.getDeclaredMethods()) {
            if (Modifier.isStatic(m.getModifiers()) && m.getName().equals("create")) {
                assertThat(Modifier.isPublic(m.getModifiers())).as("AuditEvent.create deve ser restrita ao pacote").isFalse();
            }
            assertThat(m.getName().startsWith("set")).as("setter em AuditEvent: " + m.getName()).isFalse();
        }
    }

    @Test
    void repositorioNaoExpoeUpdateNemDelete() {
        assertThat(CrudRepository.class.isAssignableFrom(AuditEventRepository.class)).isFalse();
        assertThat(PagingAndSortingRepository.class.isAssignableFrom(AuditEventRepository.class)).isFalse();
        assertThat(JpaRepository.class.isAssignableFrom(AuditEventRepository.class)).isFalse();
        for (Method m : AuditEventRepository.class.getMethods()) {
            String n = m.getName().toLowerCase();
            assertThat(n).as("método de escrita perigoso no repositório: " + m.getName())
                    .doesNotContain("delete").doesNotContain("remove").doesNotContain("update").doesNotContain("truncate");
        }
    }

    /** A API do serviço só recebe tipos fechados: nada de String/Object/Map/body/headers. */
    @Test
    void apiPublicaDoAuditServiceSoRecebeTiposTipados() {
        Set<Class<?>> permitidos = Set.of(AuditAction.class, AuditActor.class, AuditTarget.class, AuditMetadata.class);
        for (Method m : AuditService.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(m.getModifiers())) {
                continue;
            }
            for (Class<?> p : m.getParameterTypes()) {
                assertThat(permitidos).as("AuditService." + m.getName() + " param " + p.getName()).contains(p);
            }
        }
        List<String> publicos = Arrays.stream(AuditService.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers())).map(Method::getName).toList();
        assertThat(publicos).containsExactlyInAnyOrder("success", "failureIndependent", "deniedIndependent");
    }

    @Test
    void atorEAlvoRejeitamTextoQueNaoSejaEmail() {
        // Tentativa deliberada de plantar um segredo pelos únicos campos de texto livre.
        for (String segredo : new String[]{"MinhaSenhaSecreta-123", "eyJhbGciOiJIUzM4NCJ9.abcdef.ghijkl", "$2a$10$abcdefghijklmnopqrstuv",
                "Bearer abc123", "DB_PASSWORD=hunter2"}) {
            assertThatThrownBy(() -> new AuditActor(AuditActorType.USER, 1L, segredo, Role.USER, 1L))
                    .as(segredo).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new AuditTarget(1L, 1L, segredo, AuditEntityType.USER, 1L))
                    .as(segredo).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void eventoNaoPodeSerCriadoDeForaDoPacoteAudit() throws Exception {
        Method create = Arrays.stream(AuditEvent.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("create")).findFirst().orElseThrow();
        // Este teste está no MESMO pacote (com.designart.audit) apenas por conveniência; a checagem
        // relevante é que a visibilidade NÃO é pública (garantida acima) — aqui confirmamos o modificador.
        assertThat(Modifier.isPublic(create.getModifiers())).isFalse();
        assertThat(Modifier.isProtected(create.getModifiers())).isFalse();
    }
}
