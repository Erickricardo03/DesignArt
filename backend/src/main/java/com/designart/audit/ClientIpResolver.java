package com.designart.audit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Resolução CENTRAL e SEGURA do IP do cliente para a auditoria.
 * <p>
 * Padrão seguro: usa SOMENTE o endereço da conexão TCP ({@code getRemoteAddr()}).
 * O header {@code X-Forwarded-For} é um valor arbitrário enviado pelo cliente e
 * NUNCA é confiado por padrão. Só quando a conexão vem de um proxy explicitamente
 * confiável ({@code app.security.trusted-proxies}, lista de IPs/CIDRs separados
 * por vírgula, vazia por padrão) o header é considerado, lendo da DIREITA para a
 * esquerda e parando no primeiro endereço que não seja proxy confiável.
 * <p>
 * Configuração futura (proxy da Nexus): definir {@code TRUSTED_PROXIES}, por
 * exemplo {@code 10.0.0.0/8,127.0.0.1}, e garantir que o proxy SOBRESCREVA (não
 * apenas acrescente) X-Forwarded-For com o IP real da conexão que recebeu.
 * Valores malformados (incl. nomes de host) são descartados; o resultado nunca
 * passa de 45 caracteres e nenhuma resolução DNS é feita.
 */
@Component
public class ClientIpResolver {

    static final int MAX_IP_LENGTH = 45;
    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");
    private static final Pattern IPV6 = Pattern.compile("^[0-9A-Fa-f:.]{2,45}(%[A-Za-z0-9_.-]{1,15})?$");

    private final List<Cidr> trusted;

    public ClientIpResolver(@Value("${app.security.trusted-proxies:}") String trustedProxies) {
        this.trusted = parse(trustedProxies);
    }

    /** IP do cliente da requisição corrente (ou {@code null} fora de uma requisição). */
    public String resolveCurrent() {
        return org.springframework.web.context.request.RequestContextHolder.getRequestAttributes()
                instanceof org.springframework.web.context.request.ServletRequestAttributes attrs
                ? resolve(attrs.getRequest()) : null;
    }

    /** IP para a TRILHA de auditoria (ponto de extensão separado do uso em rate limit/tokens). */
    public String resolveForAudit(HttpServletRequest request) {
        return resolve(request);
    }

    /** IP do cliente para auditoria, ou {@code null} se indeterminável. */
    public String resolve(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String remote = normalizeOrNull(request.getRemoteAddr());
        if (remote == null || trusted.isEmpty() || !isTrusted(remote)) {
            return remote; // padrão: ignora completamente X-Forwarded-For
        }
        List<String> cadeia = new ArrayList<>();
        Enumeration<String> headers = request.getHeaders("X-Forwarded-For");
        while (headers != null && headers.hasMoreElements()) {
            for (String parte : headers.nextElement().split(",")) {
                cadeia.add(parte);
            }
        }
        for (int i = cadeia.size() - 1; i >= 0; i--) {
            String ip = normalizeOrNull(cadeia.get(i));
            if (ip == null) {
                return remote; // malformado: não confia no header
            }
            if (!isTrusted(ip)) {
                return ip;
            }
        }
        return remote;
    }

    private boolean isTrusted(String ip) {
        byte[] bytes = bytes(ip);
        return bytes != null && trusted.stream().anyMatch(c -> c.contains(bytes));
    }

    /** Devolve o IP em forma canônica ou {@code null} se não for um IP literal válido. */
    static String normalizeOrNull(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        int zona = s.indexOf('%');
        if (zona >= 0) {
            s = s.substring(0, zona);
        }
        if (s.isEmpty() || s.length() > MAX_IP_LENGTH) {
            return null;
        }
        byte[] b = bytes(s);
        if (b == null) {
            return null;
        }
        try {
            String canonico = InetAddress.getByAddress(b).getHostAddress();
            return canonico.length() <= MAX_IP_LENGTH ? canonico : null;
        } catch (UnknownHostException e) {
            return null;
        }
    }

    /** Bytes de um IP LITERAL (nunca faz DNS): só chama o resolver depois de validar o formato. */
    private static byte[] bytes(String s) {
        boolean v4 = IPV4.matcher(s).matches();
        boolean v6 = !v4 && s.indexOf(':') >= 0 && IPV6.matcher(s).matches();
        if (!v4 && !v6) {
            return null;
        }
        try {
            if (v4) {
                for (String octeto : s.split("\\.")) {
                    if (Integer.parseInt(octeto) > 255) {
                        return null;
                    }
                }
            }
            return InetAddress.getByName(s).getAddress();
        } catch (UnknownHostException | NumberFormatException e) {
            return null;
        }
    }

    private static List<Cidr> parse(String config) {
        List<Cidr> lista = new ArrayList<>();
        if (config == null || config.isBlank()) {
            return lista;
        }
        for (String item : config.split(",")) {
            String entry = item.trim();
            if (entry.isEmpty()) {
                continue;
            }
            String[] partes = entry.split("/", 2);
            byte[] base = bytes(partes[0].trim());
            if (base == null) {
                throw new IllegalArgumentException("app.security.trusted-proxies contém entrada inválida.");
            }
            int bits = partes.length == 2 ? Integer.parseInt(partes[1].trim()) : base.length * 8;
            if (bits < 0 || bits > base.length * 8) {
                throw new IllegalArgumentException("app.security.trusted-proxies contém prefixo inválido.");
            }
            lista.add(new Cidr(base, bits));
        }
        return lista;
    }

    private record Cidr(byte[] base, int bits) {
        boolean contains(byte[] ip) {
            if (ip.length != base.length) {
                return false;
            }
            int bytesCompletos = bits / 8;
            for (int i = 0; i < bytesCompletos; i++) {
                if (ip[i] != base[i]) {
                    return false;
                }
            }
            int resto = bits % 8;
            if (resto == 0) {
                return true;
            }
            int mascara = 0xFF << (8 - resto) & 0xFF;
            return (ip[bytesCompletos] & mascara) == (base[bytesCompletos] & mascara);
        }
    }
}
