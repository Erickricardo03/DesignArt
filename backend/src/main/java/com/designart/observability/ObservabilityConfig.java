package com.designart.observability;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObservabilityConfig {

    /**
     * O ErrorCaptureFilter pertence à cadeia de segurança (logo após o JWT). Desliga o registro automático de
     * Filter beans para que ele não rode também fora dela (onde o TenantContext já estaria limpo).
     */
    @Bean
    public FilterRegistrationBean<ErrorCaptureFilter> errorCaptureFilterRegistration(ErrorCaptureFilter filter) {
        FilterRegistrationBean<ErrorCaptureFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
