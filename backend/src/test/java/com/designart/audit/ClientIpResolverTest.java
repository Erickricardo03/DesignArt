package com.designart.audit;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Resolução segura do IP do cliente: X-Forwarded-For nunca é confiado por padrão. */
class ClientIpResolverTest {

    private MockHttpServletRequest req(String remote, String... forwardedFor) {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setRemoteAddr(remote);
        for (String v : forwardedFor) {
            r.addHeader("X-Forwarded-For", v);
        }
        return r;
    }

    @Test
    void porPadraoUsaSomenteOIpDaConexao_ignoraXForwardedFor() {
        ClientIpResolver resolver = new ClientIpResolver("");
        assertThat(resolver.resolve(req("203.0.113.5", "1.2.3.4"))).isEqualTo("203.0.113.5");
        assertThat(resolver.resolve(req("203.0.113.5", "10.0.0.1, 1.2.3.4"))).isEqualTo("203.0.113.5");
    }

    @Test
    void proxyNaoConfiavelNaoPodeForjarOIp() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.0/8");
        // A conexão vem de 198.51.100.9 (não é proxy confiável): o header é ignorado.
        assertThat(resolver.resolve(req("198.51.100.9", "1.2.3.4"))).isEqualTo("198.51.100.9");
    }

    @Test
    void proxyConfiavelUsaOEnderecoMaisADireitaQueNaoSejaProxy() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.0/8, 127.0.0.1");
        // cliente forjou "9.9.9.9" à esquerda; o proxy acrescentou o IP real 203.0.113.77
        assertThat(resolver.resolve(req("10.1.2.3", "9.9.9.9, 203.0.113.77"))).isEqualTo("203.0.113.77");
        // cadeia com dois proxies confiáveis à direita
        assertThat(resolver.resolve(req("10.1.2.3", "9.9.9.9, 203.0.113.77, 10.9.9.9"))).isEqualTo("203.0.113.77");
        // múltiplas linhas do header
        assertThat(resolver.resolve(req("127.0.0.1", "9.9.9.9", "203.0.113.77"))).isEqualTo("203.0.113.77");
    }

    @Test
    void semXForwardedForOuTodosConfiaveisCaiNoIpDaConexao() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.0/8");
        assertThat(resolver.resolve(req("10.0.0.2"))).isEqualTo("10.0.0.2");
        assertThat(resolver.resolve(req("10.0.0.2", "10.0.0.7, 10.0.0.8"))).isEqualTo("10.0.0.2");
    }

    @Test
    void valorMalformadoNoHeaderNaoEConfiado() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.0/8");
        for (String lixo : new String[]{"nome-de-host.exemplo.com", "<script>", "999.1.1.1", "1.2.3", "senha-secreta",
                "1.2.3.4; DROP TABLE", "a".repeat(200), ""}) {
            assertThat(resolver.resolve(req("10.0.0.2", lixo))).as(lixo).isEqualTo("10.0.0.2");
        }
    }

    @Test
    void ipv6EIpv4Mapeado() {
        ClientIpResolver resolver = new ClientIpResolver("::1, fd00::/8");
        assertThat(resolver.resolve(req("2001:db8::1"))).isEqualTo("2001:db8:0:0:0:0:0:1");
        assertThat(resolver.resolve(req("::1", "2001:db8::2"))).isEqualTo("2001:db8:0:0:0:0:0:2");
        assertThat(resolver.resolve(req("fd00::5", "203.0.113.5"))).isEqualTo("203.0.113.5");
    }

    @Test
    void resultadoNuncaExcede45Caracteres_eEntradaNulaOuInvalidaDaNull() {
        ClientIpResolver resolver = new ClientIpResolver("");
        assertThat(resolver.resolve(null)).isNull();
        assertThat(resolver.resolve(req("nao-e-ip"))).isNull();
        assertThat(resolver.resolve(req("2001:db8:85a3:8d3:1319:8a2e:370:7348"))).hasSizeLessThanOrEqualTo(45);
    }

    @Test
    void cidrComPrefixoNaoAlinhadoAoByte() {
        ClientIpResolver resolver = new ClientIpResolver("172.16.0.0/12");
        assertThat(resolver.resolve(req("172.31.255.255", "203.0.113.1"))).isEqualTo("203.0.113.1");  // dentro do /12
        assertThat(resolver.resolve(req("172.32.0.1", "203.0.113.1"))).isEqualTo("172.32.0.1");        // fora do /12
    }

    @Test
    void configuracaoInvalidaFalhaAoSubir() {
        assertThatThrownBy(() -> new ClientIpResolver("host-invalido")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ClientIpResolver("10.0.0.0/40")).isInstanceOf(IllegalArgumentException.class);
    }
}
