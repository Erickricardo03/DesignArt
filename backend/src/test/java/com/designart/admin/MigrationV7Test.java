package com.designart.admin;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Trava as migrations aprovadas: checksum (mesmo algoritmo do Flyway: CRC32 linha a linha) de V1–V7.
 * Qualquer edição de uma migration já aprovada quebra este teste.
 */
class MigrationV7Test {

    private static final Path DIR = Path.of("src/main/resources/db/migration");
    private static final Map<String, Integer> APROVADOS = Map.of(
            "V1__initial_schema.sql", -262384729,
            "V2__add_multitenancy.sql", 1794476632,
            "V3__identity_and_roles.sql", 412742310,
            "V4__audit_events.sql", 2109276943,
            "V5__user_action_tokens.sql", -447843649,
            "V6__plans_subscriptions_and_entitlements.sql", 1065986832,
            "V7__billing_invoices_and_payment_history.sql", 1743877123,
            "V8__support_sessions_and_observability.sql", 1452444455);

    static int checksum(Path file) throws IOException {
        CRC32 crc = new CRC32();
        try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            boolean first = true;
            while ((line = r.readLine()) != null) {
                if (first && !line.isEmpty() && line.charAt(0) == '﻿') {
                    line = line.substring(1);
                }
                first = false;
                crc.update(line.getBytes(StandardCharsets.UTF_8));
            }
        }
        return (int) crc.getValue();
    }

    @Test
    void migrationsV1aV7TemChecksumAprovado() throws IOException {
        for (var e : APROVADOS.entrySet()) {
            assertThat(checksum(DIR.resolve(e.getKey()))).as(e.getKey()).isEqualTo(e.getValue());
        }
    }

    @Test
    void v8DefineSessoesDeSuporteEIncidentesComRestricoesFortes() throws IOException {
        String sql = Files.readAllLines(DIR.resolve("V8__support_sessions_and_observability.sql")).stream()
                .filter(l -> !l.trim().startsWith("--")).collect(java.util.stream.Collectors.joining(" ")).toLowerCase();
        assertThat(sql).contains("create table support_sessions").contains("create table application_error_events")
                .contains("support_sessions_one_active_key").contains("support_sessions_expiry_chk").contains("support_sessions_reason_chk")
                .contains("aee_open_fingerprint_key").contains("aee_path_chk").contains("support_sessions_guard");
        // não guarda segredo de sessão, corpo de requisição nem stack trace
        assertThat(sql).doesNotContain("token").doesNotContain("password").doesNotContain("stack").doesNotContain("request_body")
                .doesNotContain("query_string").doesNotContain("cookie").doesNotContain("header");
    }

    @Test
    void v7ExisteENaoUsaTiposImprecisosParaDinheiro() throws IOException {
        Path v7 = DIR.resolve("V7__billing_invoices_and_payment_history.sql");
        assertThat(v7).exists();
        String sql = Files.readAllLines(v7).stream().filter(l -> !l.trim().startsWith("--")).collect(java.util.stream.Collectors.joining(" ")).toLowerCase();
        assertThat(sql).contains("create table billing_invoices").contains("create table invoice_payments")
                .contains("numeric(12,2)")
                .doesNotContain(" float").doesNotContain(" double").doesNotContain(" real").doesNotContain("money");
        assertThat(sql).contains("billing_invoices_paid_chk").contains("billing_invoices_canceled_chk")
                .contains("billing_invoices_amount_chk").contains("billing_invoices_period_key");
        // só existem V1..V8 (V8 é a última; nenhuma migration paralela)
        try (var files = Files.list(DIR)) {
            assertThat(files.map(p -> p.getFileName().toString()).filter(n -> n.startsWith("V")).count()).isEqualTo(8);
        }
    }
}
