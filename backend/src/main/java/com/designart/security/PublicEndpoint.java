package com.designart.security;

import java.lang.annotation.*;

/**
 * Marca um endpoint deliberadamente PÚBLICO (sem autenticação). A lista de
 * endpoints públicos é pequena, explícita e documentada; o teste
 * EndpointAuthorizationCoverageTest falha se surgir um endpoint sem regra de
 * autorização ou um público fora da lista aprovada. O acesso anônimo real é
 * liberado no SecurityConfig, por caminho exato.
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PublicEndpoint {
}
