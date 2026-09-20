package com.designart.support;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registra o portão fail-closed do Modo Suporte apenas na árvore de operações sobre o tenant. */
@Configuration
@RequiredArgsConstructor
public class SupportWebConfig implements WebMvcConfigurer {

    private final SupportAccessInterceptor accessInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(accessInterceptor).addPathPatterns(SupportAccessInterceptor.PATH_PATTERN);
    }
}
