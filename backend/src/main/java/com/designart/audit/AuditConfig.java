package com.designart.audit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class AuditConfig {

    /** Relógio único da aplicação (UTC); injetável para testes. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
