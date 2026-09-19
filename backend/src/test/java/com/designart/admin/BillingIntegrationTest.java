package com.designart.admin;

import com.designart.audit.AuditAction;
import com.designart.audit.AuditEvent;
import com.designart.billing.*;
import com.designart.exception.ConflictException;
import com.designart.model.Tenant;
import com.designart.security.Role;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * Financeiro SaaS (Fase 4.4.2) em H2: geração idempotente, preço contratado imutável, pagamento manual,
 * vencimento/carência, suspensão por inadimplência, endpoints, dashboard, auditoria e isolamento.
 * As constraints de banco (CHECK/FK/índices parciais) são validadas à parte contra PostgreSQL 16.
 */
class BillingIntegrationTest extends AdminTestBase {

    @Autowired BillingService billingService;
    @Autowired InvoiceRepository invoiceRepository;
    @Autowired InvoicePaymentRepository paymentRepository;

    private String sup;

    private String sup() throws Exception {
        if (sup == null) {
            sup = superJwt();
        }
        return sup;
    }

    private LocalDate hoje() {
        return LocalDate.now(clock);
    }

    /** Tenant ATIVO com assinatura ACTIVE e termos contratados (via API). */
    private Tenant tenantComContrato(String valor) throws Exception {
        Tenant t = novoTenant("ATIVO");
        assinatura(t.getId(), plano(true), SubscriptionStatus.ACTIVE);
        Map<String, Object> termos = new HashMap<>();
        termos.put("contractedAmount", new BigDecimal(valor));
        MvcResult r = putJson("/api/admin/tenants/" + t.getId() + "/billing-terms", termos, sup());
        assertThat(r.getResponse().getStatus()).as(corpo(r)).isEqualTo(200);
        return t;
    }

    private long gerar(Tenant t) throws Exception {
        MvcResult r = postJson("/api/admin/tenants/" + t.getId() + "/invoices", Map.of(), sup());
        assertThat(r.getResponse().getStatus()).as(corpo(r)).isIn(200, 201);
        return corpoJson(r).get("invoice").get("id").asLong();
    }

    /** Força datas/estado (simula o passar do tempo sem depender do dia em que o teste roda). */
    private void fixar(long invoiceId, String status, LocalDate vence, LocalDate carenciaAte) {
        jdbc.update("update billing_invoices set status = ?, due_date = ?, grace_ends_on = ?, paid_at = null, canceled_at = null where id = ?",
                status, java.sql.Date.valueOf(vence), java.sql.Date.valueOf(carenciaAte), invoiceId);
    }

    private JsonNode fatura(long id) throws Exception {
        MvcResult r = obter("/api/admin/billing/invoices/" + id, sup());
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        return corpoJson(r);
    }

    private List<Long> ids(String path) throws Exception {
        MvcResult r = obter(path, sup());
        assertThat(r.getResponse().getStatus()).as(path + " " + corpo(r)).isEqualTo(200);
        List<Long> out = new ArrayList<>();
        corpoJson(r).get("items").forEach(n -> out.add(n.get("id").asLong()));
        return out;
    }

    // dumpDesde: texto de TODAS as colunas dos eventos novos (prova ausência de valores financeiros)
    private List<AuditAction> acoesDesde(long antes) {
        return auditRepository.findAllByOrderByIdAsc().stream().filter(e -> e.getId() > antes).map(AuditEvent::getAction).toList();
    }

    // ------------------------------------------------------------------ termos e dinheiro
    @Test
    void termosContratados_usamBigDecimalExato_eRejeitamValoresInvalidos() throws Exception {
        Tenant t = tenantComContrato("149.90");
        BigDecimal noBanco = jdbc.queryForObject("select contracted_amount from subscriptions where tenant_id = ?", BigDecimal.class, t.getId());
        assertThat(noBanco).isEqualByComparingTo("149.90");
        assertThat(subscriptionRepository.findByTenantId(t.getId()).orElseThrow().getContractedAmount()).isEqualTo(new BigDecimal("149.90"));
        assertThat(corpoJson(obter("/api/admin/tenants/" + t.getId() + "/billing-terms", sup())).get("contractedAmount").decimalValue())
                .isEqualByComparingTo("149.90");

        for (String invalido : List.of("0", "-1", "10.999", "99999999999")) {
            Map<String, Object> m = new HashMap<>();
            m.put("contractedAmount", new BigDecimal(invalido));
            assertThat(putJson("/api/admin/tenants/" + t.getId() + "/billing-terms", m, sup()).getResponse().getStatus()).as(invalido).isEqualTo(400);
        }
        for (Map<String, Object> m : List.<Map<String, Object>>of(Map.of("currency", "USD"), Map.of("billingDay", 0), Map.of("billingDay", 29),
                Map.of("graceDays", -1), Map.of("graceDays", 91))) {
            assertThat(putJson("/api/admin/tenants/" + t.getId() + "/billing-terms", m, sup()).getResponse().getStatus()).as(m.toString()).isEqualTo(400);
        }
        assertThat(subscriptionRepository.findByTenantId(t.getId()).orElseThrow().getContractedAmount()).isEqualTo(new BigDecimal("149.90"));
    }

    @Test
    void naoHaFloatNemDoubleEmValoresMonetarios() {
        for (Class<?> c : List.of(Invoice.class, InvoicePayment.class, Subscription.class)) {
            for (var f : c.getDeclaredFields()) {
                assertThat(f.getType()).as(c.getSimpleName() + "." + f.getName()).isNotIn(double.class, float.class, Double.class, Float.class);
            }
        }
        for (Class<?> c : com.designart.admin.dto.AdminDtos.class.getDeclaredClasses()) {
            for (var f : c.getDeclaredFields()) {
                assertThat(f.getType()).as(c.getSimpleName() + "." + f.getName()).isNotIn(double.class, float.class, Double.class, Float.class);
            }
        }
    }

    // ------------------------------------------------------------------ geração
    @Test
    void geracaoEIdempotente_umaCobrancaPorPeriodo_edatasDeterministicas() throws Exception {
        Tenant t = tenantComContrato("149.90");
        Map<String, Object> termos = Map.of("billingDay", 15, "graceDays", 4);
        assertThat(putJson("/api/admin/tenants/" + t.getId() + "/billing-terms", termos, sup()).getResponse().getStatus()).isEqualTo(200);
        long antes = maxAuditId();

        MvcResult primeira = postJson("/api/admin/tenants/" + t.getId() + "/invoices", Map.of(), sup());
        assertThat(primeira.getResponse().getStatus()).isEqualTo(201);
        JsonNode inv = corpoJson(primeira).get("invoice");
        assertThat(corpoJson(primeira).get("created").asBoolean()).isTrue();
        LocalDate periodo = hoje().withDayOfMonth(1);
        assertThat(LocalDate.parse(inv.get("referencePeriod").asText())).isEqualTo(periodo);
        assertThat(LocalDate.parse(inv.get("dueDate").asText())).isEqualTo(periodo.withDayOfMonth(15));
        assertThat(LocalDate.parse(inv.get("graceEndsOn").asText())).isEqualTo(periodo.withDayOfMonth(15).plusDays(4));
        assertThat(inv.get("amount").decimalValue()).isEqualByComparingTo("149.90");
        assertThat(inv.get("currency").asText()).isEqualTo("BRL");
        assertThat(inv.get("status").asText()).isEqualTo(hoje().isAfter(periodo.withDayOfMonth(15)) ? "OVERDUE" : "OPEN");
        assertThat(inv.get("tenantId").asLong()).isEqualTo(t.getId());

        MvcResult segunda = postJson("/api/admin/tenants/" + t.getId() + "/invoices", Map.of("referencePeriod", periodo.toString()), sup());
        assertThat(segunda.getResponse().getStatus()).isEqualTo(200);
        assertThat(corpoJson(segunda).get("created").asBoolean()).isFalse();
        assertThat(corpoJson(segunda).get("invoice").get("id").asLong()).isEqualTo(inv.get("id").asLong());
        assertThat(invoiceRepository.count(byTenant(t.getId()))).isEqualTo(1);
        assertThat(acoesDesde(antes)).containsExactly(AuditAction.INVOICE_CREATED); // reprocessar não audita de novo
    }

    private org.springframework.data.jpa.domain.Specification<Invoice> byTenant(Long id) {
        return (r, q, cb) -> cb.equal(r.get("tenantId"), id);
    }

    @Test
    void geracaoEmParalelo_criaExatamenteUma() throws Exception {
        Tenant t = tenantComContrato("50.00");
        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            CyclicBarrier largada = new CyclicBarrier(6);
            List<Future<Boolean>> fs = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                fs.add(pool.submit(() -> {
                    largada.await();
                    try {
                        return billingService.generate(t.getId(), null).created();
                    } catch (RuntimeException e) {
                        return false;
                    }
                }));
            }
            int criadas = 0;
            for (Future<Boolean> f : fs) {
                criadas += f.get() ? 1 : 0;
            }
            assertThat(criadas).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(invoiceRepository.count(byTenant(t.getId()))).isEqualTo(1);
    }

    @Test
    void geracaoRecusaSituacoesInvalidas() throws Exception {
        Tenant semAssinatura = novoTenant("ATIVO");
        assertThat(postJson("/api/admin/tenants/" + semAssinatura.getId() + "/invoices", Map.of(), sup()).getResponse().getStatus()).isEqualTo(404);
        assertThat(postJson("/api/admin/tenants/999999999/invoices", Map.of(), sup()).getResponse().getStatus()).isEqualTo(404);

        Tenant semValor = novoTenant("ATIVO");
        assinatura(semValor.getId(), plano(true), SubscriptionStatus.ACTIVE);
        assertThat(postJson("/api/admin/tenants/" + semValor.getId() + "/invoices", Map.of(), sup()).getResponse().getStatus()).isEqualTo(409);

        Tenant t = tenantComContrato("10.00");
        String mesAtual = hoje().withDayOfMonth(1).toString();
        assertThat(postJson("/api/admin/tenants/" + t.getId() + "/invoices", Map.of("referencePeriod", hoje().withDayOfMonth(2).toString()), sup())
                .getResponse().getStatus()).as("não é dia 1").isEqualTo(400);
        assertThat(postJson("/api/admin/tenants/" + t.getId() + "/invoices", Map.of("referencePeriod", hoje().plusMonths(1).withDayOfMonth(1).toString()), sup())
                .getResponse().getStatus()).as("futura").isEqualTo(400);
        assertThat(postJson("/api/admin/tenants/" + t.getId() + "/invoices", Map.of("referencePeriod", hoje().minusMonths(3).withDayOfMonth(1).toString()), sup())
                .getResponse().getStatus()).as("antes da assinatura").isEqualTo(400);
        assertThat(mesAtual).isNotNull();

        Tenant cancelada = tenantComContrato("10.00");
        assertThat(putJson("/api/admin/tenants/" + cancelada.getId() + "/subscription",
                Map.of("planCode", planRepository.findById(subscriptionRepository.findByTenantId(cancelada.getId()).orElseThrow().getPlanId()).orElseThrow().getCode(),
                        "status", "CANCELED"), sup()).getResponse().getStatus()).isEqualTo(200);
        assertThat(postJson("/api/admin/tenants/" + cancelada.getId() + "/invoices", Map.of(), sup()).getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void precoContratadoEHistoricoNaoSaoAlteradosRetroativamente() throws Exception {
        Tenant t = tenantComContrato("149.90");
        long id = gerar(t);
        // muda o contrato (e o plano do catálogo não tem preço): a cobrança emitida permanece intacta
        assertThat(putJson("/api/admin/tenants/" + t.getId() + "/billing-terms", Map.of("contractedAmount", new BigDecimal("199.90"), "billingDay", 20), sup())
                .getResponse().getStatus()).isEqualTo(200);
        assertThat(fatura(id).get("amount").decimalValue()).isEqualByComparingTo("149.90");
        assertThat(jdbc.queryForObject("select amount from billing_invoices where id = ?", BigDecimal.class, id)).isEqualByComparingTo("149.90");

        // cancelar e reemitir o mesmo período: nova cobrança com o valor vigente; a cancelada permanece
        assertThat(postJson("/api/admin/billing/invoices/" + id + "/cancel", Map.of(), sup()).getResponse().getStatus()).isEqualTo(200);
        long novo = gerar(t);
        assertThat(novo).isNotEqualTo(id);
        assertThat(fatura(novo).get("amount").decimalValue()).isEqualByComparingTo("199.90");
        assertThat(fatura(id).get("status").asText()).isEqualTo("CANCELED");
        assertThat(fatura(id).get("amount").decimalValue()).isEqualByComparingTo("149.90");
    }

    // ------------------------------------------------------------------ pagamento manual
    @Test
    void pagamentoManual_registraHistorico_marcaPaga_eEAuditado() throws Exception {
        Tenant t = tenantComContrato("149.90");
        long id = gerar(t);
        long antes = maxAuditId();
        MvcResult r = postJson("/api/admin/billing/invoices/" + id + "/pay",
                Map.of("amount", new BigDecimal("149.90"), "externalRef", "COMPROVANTE-123", "tenantId", 999999), sup());
        assertThat(r.getResponse().getStatus()).as(corpo(r)).isEqualTo(200);
        JsonNode inv = corpoJson(r);
        assertThat(inv.get("status").asText()).isEqualTo("PAID");
        assertThat(inv.get("paidAt").isNull()).isFalse();
        assertThat(inv.get("tenantId").asLong()).as("tenantId do corpo é ignorado").isEqualTo(t.getId());

        List<InvoicePayment> pagamentos = paymentRepository.findByInvoiceIdOrderByOccurredAtAscIdAsc(id);
        assertThat(pagamentos).hasSize(1);
        InvoicePayment p = pagamentos.get(0);
        assertThat(p.getMethod()).isEqualTo(PaymentMethod.MANUAL);
        assertThat(p.getKind()).isEqualTo(PaymentKind.PAYMENT);
        assertThat(p.getAmount()).isEqualTo(new BigDecimal("149.90"));
        assertThat(p.getTenantId()).isEqualTo(t.getId());
        assertThat(p.getExternalRef()).isEqualTo("COMPROVANTE-123");
        assertThat(p.getRegisteredByUserId()).isNotNull();
        assertThat(userRepository.findById(p.getRegisteredByUserId()).orElseThrow().getRole()).isEqualTo(Role.SUPER_ADMIN);

        JsonNode hist = corpoJson(obter("/api/admin/billing/invoices/" + id + "/payments", sup()));
        assertThat(hist).hasSize(1);
        assertThat(hist.get(0).get("method").asText()).isEqualTo("MANUAL");
        assertThat(acoesDesde(antes)).containsExactly(AuditAction.PAYMENT_RECORDED, AuditAction.INVOICE_MARKED_PAID);
        // nenhum valor financeiro nem referência na trilha
        String dump = jdbc.queryForList("select * from audit_events where id > ?", antes).toString();
        assertThat(dump).doesNotContain("149").doesNotContain("COMPROVANTE").doesNotContain("amount");
    }

    @Test
    void pagamentoDuplicado_canceladaEValorDivergente_saoRecusados() throws Exception {
        Tenant t = tenantComContrato("80.00");
        long id = gerar(t);
        String pay = "/api/admin/billing/invoices/" + id + "/pay";
        assertThat(postJson(pay, Map.of("amount", new BigDecimal("79.99")), sup()).getResponse().getStatus()).isEqualTo(400);
        assertThat(postJson(pay, Map.of("amount", new BigDecimal("80.00"), "occurredAt", LocalDateTime.now().plusDays(3).toString()), sup())
                .getResponse().getStatus()).as("data futura").isEqualTo(400);
        assertThat(postJson(pay, Map.of("amount", new BigDecimal("80.00"), "externalRef", "../etc/passwd"), sup()).getResponse().getStatus()).isEqualTo(400);
        assertThat(postJson(pay, Map.of(), sup()).getResponse().getStatus()).isEqualTo(400);
        assertThat(postJson(pay, Map.of("amount", new BigDecimal("80.00")), sup()).getResponse().getStatus()).isEqualTo(200);
        assertThat(postJson(pay, Map.of("amount", new BigDecimal("80.00")), sup()).getResponse().getStatus()).as("duplicado").isEqualTo(409);
        assertThat(paymentRepository.findByInvoiceIdOrderByOccurredAtAscIdAsc(id)).hasSize(1);
        assertThat(postJson("/api/admin/billing/invoices/" + id + "/cancel", Map.of(), sup()).getResponse().getStatus()).as("paga não cancela").isEqualTo(409);

        Tenant t2 = tenantComContrato("80.00");
        long cancelada = gerar(t2);
        assertThat(postJson("/api/admin/billing/invoices/" + cancelada + "/cancel", Map.of(), sup()).getResponse().getStatus()).isEqualTo(200);
        assertThat(postJson("/api/admin/billing/invoices/" + cancelada + "/cancel", Map.of(), sup()).getResponse().getStatus()).as("cancelar 2x").isEqualTo(409);
        assertThat(postJson("/api/admin/billing/invoices/" + cancelada + "/pay", Map.of("amount", new BigDecimal("80.00")), sup()).getResponse().getStatus())
                .as("cancelada não paga").isEqualTo(409);
        assertThat(paymentRepository.findByInvoiceIdOrderByOccurredAtAscIdAsc(cancelada)).isEmpty();
        assertThat(jdbc.queryForObject("select canceled_at from billing_invoices where id = ?", Timestamp.class, cancelada)).isNotNull();
        assertThat(jdbc.queryForObject("select paid_at from billing_invoices where id = ?", Timestamp.class, cancelada)).isNull();
        assertThat(postJson("/api/admin/billing/invoices/999999999/pay", Map.of("amount", new BigDecimal("1.00")), sup()).getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void pagamentoEmParalelo_soUmVence() throws Exception {
        Tenant t = tenantComContrato("30.00");
        long id = gerar(t);
        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            CyclicBarrier largada = new CyclicBarrier(6);
            List<Future<Boolean>> fs = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                fs.add(pool.submit(() -> {
                    largada.await();
                    try {
                        billingService.pay(id, new BigDecimal("30.00"), null, null);
                        return true;
                    } catch (ConflictException e) {
                        return false;
                    }
                }));
            }
            int ok = 0;
            for (Future<Boolean> f : fs) {
                ok += f.get() ? 1 : 0;
            }
            assertThat(ok).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(paymentRepository.findByInvoiceIdOrderByOccurredAtAscIdAsc(id)).hasSize(1);
    }

    // ------------------------------------------------------------------ vencimento e carência
    @Test
    void vencimentoEDerivadoNoBackend_eEIdempotente() throws Exception {
        Tenant vencida = tenantComContrato("10.00");
        Tenant venceHoje = tenantComContrato("10.00");
        long a = gerar(vencida);
        long b = gerar(venceHoje);
        fixar(a, "OPEN", hoje().minusDays(1), hoje().plusDays(4));
        fixar(b, "OPEN", hoje(), hoje().plusDays(5));

        assertThat(fatura(a).get("status").asText()).as("dia seguinte ao vencimento").isEqualTo("OVERDUE");
        assertThat(fatura(b).get("status").asText()).as("no dia do vencimento continua aberta").isEqualTo("OPEN");
        assertThat(jdbc.queryForObject("select status from billing_invoices where id = ?", String.class, a)).as("persistido").isEqualTo("OVERDUE");
        assertThat(corpoJson(postJson("/api/admin/billing/refresh", Map.of(), sup())).get("invoicesMarkedOverdue").asInt()).isZero();
        assertThat(billingService.refreshOverdue()).isZero();

        // amanhã (relógio injetável): a que vencia hoje passa a vencida
        clock.advance(Duration.ofDays(1));
        assertThat(fatura(b).get("status").asText()).isEqualTo("OVERDUE");
    }

    @Test
    void fasesDeCobranca_bordasDeCarencia_eListagens() throws Exception {
        Tenant proxima = tenantComContrato("10.00"), venceHoje = tenantComContrato("10.00"), foraJanela = tenantComContrato("10.00");
        Tenant carenciaHoje = tenantComContrato("10.00"), carenciaAmanha = tenantComContrato("10.00");
        Tenant foraCarencia = tenantComContrato("10.00"), paga = tenantComContrato("10.00");
        long p = gerar(proxima), vh = gerar(venceHoje), fj = gerar(foraJanela), ch = gerar(carenciaHoje), ca = gerar(carenciaAmanha),
                fc = gerar(foraCarencia), pg = gerar(paga);
        fixar(p, "OPEN", hoje().plusDays(3), hoje().plusDays(8));
        fixar(vh, "OPEN", hoje(), hoje().plusDays(5));
        fixar(fj, "OPEN", hoje().plusDays(8), hoje().plusDays(13));          // além da janela de 7 dias
        fixar(ch, "OVERDUE", hoje().minusDays(5), hoje());                    // último dia da carência: ainda em carência
        fixar(ca, "OVERDUE", hoje().minusDays(2), hoje().plusDays(3));
        fixar(fc, "OVERDUE", hoje().minusDays(6), hoje().minusDays(1));       // 1 dia após a carência: fora
        assertThat(postJson("/api/admin/billing/invoices/" + pg + "/pay", Map.of("amount", new BigDecimal("10.00")), sup()).getResponse().getStatus()).isEqualTo(200);

        List<Long> upcoming = ids("/api/admin/billing/invoices/upcoming?size=100");
        assertThat(upcoming).contains(p, vh).doesNotContain(fj, ch, fc, pg);
        assertThat(ids("/api/admin/billing/invoices/upcoming?days=8&size=100")).contains(fj);
        assertThat(ids("/api/admin/billing/invoices/upcoming?days=0&size=100")).contains(vh).doesNotContain(p);
        assertThat(status(get("/api/admin/billing/invoices/upcoming?days=999"), sup())).isEqualTo(400);

        List<Long> overdue = ids("/api/admin/billing/invoices/overdue?size=100");
        assertThat(overdue).contains(ch, ca, fc).doesNotContain(p, vh, pg);
        assertThat(ids("/api/admin/billing/invoices/in-grace?size=100")).contains(ch, ca).doesNotContain(fc);
        assertThat(ids("/api/admin/billing/invoices/beyond-grace?size=100")).contains(fc).doesNotContain(ch, ca);

        assertThat(fatura(p).get("phase").asText()).isEqualTo("UPCOMING");
        assertThat(fatura(fj).get("phase").asText()).isEqualTo("CURRENT");
        assertThat(fatura(ch).get("phase").asText()).isEqualTo("IN_GRACE");
        assertThat(fatura(fc).get("phase").asText()).isEqualTo("BEYOND_GRACE");
        assertThat(fatura(pg).get("phase").asText()).isEqualTo("NONE");
    }

    // ------------------------------------------------------------------ filtros e paginação
    @Test
    void filtrosPorStatusETenant_paginacaoEIsolamentoEntreTenants() throws Exception {
        Tenant a = tenantComContrato("10.00"), b = tenantComContrato("20.00");
        long ia = gerar(a), ib = gerar(b);
        assertThat(postJson("/api/admin/billing/invoices/" + ib + "/pay", Map.of("amount", new BigDecimal("20.00")), sup()).getResponse().getStatus()).isEqualTo(200);

        assertThat(ids("/api/admin/tenants/" + a.getId() + "/invoices")).containsExactly(ia);
        assertThat(ids("/api/admin/tenants/" + b.getId() + "/invoices")).containsExactly(ib);
        assertThat(ids("/api/admin/billing/invoices?tenantId=" + a.getId())).containsExactly(ia);
        assertThat(ids("/api/admin/billing/invoices?tenantId=" + b.getId() + "&status=PAID")).containsExactly(ib);
        assertThat(ids("/api/admin/billing/invoices?tenantId=" + a.getId() + "&status=PAID")).isEmpty();
        assertThat(status(get("/api/admin/billing/invoices?status=INEXISTENTE"), sup())).isEqualTo(400);
        assertThat(status(get("/api/admin/billing/invoices?tenantId=999999999"), sup())).isEqualTo(404);
        assertThat(ids("/api/admin/billing/payments?tenantId=" + a.getId())).isEmpty();
        assertThat(ids("/api/admin/billing/payments?tenantId=" + b.getId())).hasSize(1);
        // a cobrança de um tenant não aparece no financeiro de outro
        JsonNode fin = corpoJson(obter("/api/admin/tenants/" + a.getId() + "/billing", sup()));
        assertThat(fin.get("recentInvoices")).hasSize(1);
        assertThat(fin.get("recentInvoices").get(0).get("id").asLong()).isEqualTo(ia);
        assertThat(fin.get("lastPaymentAt").isNull()).isTrue();
        assertThat(corpoJson(obter("/api/admin/tenants/" + b.getId() + "/billing", sup())).get("lastPaymentAt").isNull()).isFalse();

        // paginação
        Tenant c = tenantComContrato("5.00");
        for (int mes = 0; mes < 1; mes++) {
            gerar(c);
        }
        MvcResult pagina = obter("/api/admin/billing/invoices?size=2&page=0", sup());
        JsonNode j = corpoJson(pagina);
        assertThat(j.get("items").size()).isLessThanOrEqualTo(2);
        assertThat(j.get("size").asInt()).isEqualTo(2);
        assertThat(j.get("total").asLong()).isGreaterThanOrEqualTo(3);
        assertThat(corpoJson(obter("/api/admin/billing/invoices?size=100000", sup())).get("size").asInt()).as("tamanho máximo").isEqualTo(100);
    }

    // ------------------------------------------------------------------ suspensão e reativação
    @Test
    void suspensaoPorInadimplencia_preservaDivida_assinatura_entitlements_eBranding() throws Exception {
        Tenant t = tenantComContrato("149.90");
        criarUsuario("inad.admin@teste.local", Role.TENANT_ADMIN, t.getId());
        String jwtCliente = login("inad.admin@teste.local");
        TenantBranding br = new TenantBranding();
        br.setTenantId(t.getId());
        br.setPrimaryColor("#112233");
        br.setUpdatedAt(LocalDateTime.now());
        brandingRepository.save(br);
        String entitlementsAntes = corpo(obter("/api/admin/tenants/" + t.getId() + "/entitlements", sup()));

        // sem cobrança vencida: recusada
        assertThat(postJson("/api/admin/tenants/" + t.getId() + "/suspend-non-payment", Map.of(), sup()).getResponse().getStatus()).isEqualTo(409);
        long id = gerar(t);
        fixar(id, "OPEN", hoje().minusDays(20), hoje().minusDays(15));
        long antes = maxAuditId();

        MvcResult r = postJson("/api/admin/tenants/" + t.getId() + "/suspend-non-payment", Map.of(), sup());
        assertThat(r.getResponse().getStatus()).as(corpo(r)).isEqualTo(200);
        assertThat(corpoJson(r).get("status").asText()).isEqualTo("SUSPENSO");
        Tenant suspenso = tenantRepository.findById(t.getId()).orElseThrow();
        assertThat(suspenso.getSuspensionReason()).isEqualTo(com.designart.model.SuspensionReason.NON_PAYMENT);
        assertThat(suspenso.getSuspendedAt()).isNotNull();
        assertThat(acoesDesde(antes)).containsExactly(AuditAction.TENANT_SUSPENDED_NON_PAYMENT);

        // JWT existente deixa de funcionar (AccessPolicy) e login novo também
        assertThat(status(get("/api/usuarios"), jwtCliente)).isIn(401, 403);
        assertThat(tentarLogin("inad.admin@teste.local", SENHA).getResponse().getStatus()).isNotEqualTo(200);

        // nada mais mudou: dívida, assinatura, entitlements e branding
        assertThat(fatura(id).get("status").asText()).isEqualTo("OVERDUE");
        assertThat(fatura(id).get("amount").decimalValue()).isEqualByComparingTo("149.90");
        assertThat(subscriptionRepository.findByTenantId(t.getId()).orElseThrow().getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(corpo(obter("/api/admin/tenants/" + t.getId() + "/entitlements", sup()))).isEqualTo(entitlementsAntes);
        assertThat(brandingRepository.findById(t.getId()).orElseThrow().getPrimaryColor()).isEqualTo("#112233");
        assertThat(postJson("/api/admin/tenants/" + t.getId() + "/suspend-non-payment", Map.of(), sup()).getResponse().getStatus()).as("já suspensa").isEqualTo(409);
        JsonNode fin = corpoJson(obter("/api/admin/tenants/" + t.getId() + "/billing", sup()));
        assertThat(fin.get("suspensionReason").asText()).isEqualTo("NON_PAYMENT");
        assertThat(fin.get("invoicesBeyondGrace").asInt()).isEqualTo(1);

        // reativação: NÃO presume que a dívida sumiu
        long antes2 = maxAuditId();
        assertThat(postJson("/api/admin/tenants/" + t.getId() + "/reactivate", Map.of(), sup()).getResponse().getStatus()).isEqualTo(200);
        Tenant reativado = tenantRepository.findById(t.getId()).orElseThrow();
        assertThat(reativado.getStatus()).isEqualTo("ATIVO");
        assertThat(reativado.getSuspensionReason()).isNull();
        assertThat(reativado.getSuspendedAt()).isNull();
        assertThat(fatura(id).get("status").asText()).as("dívida permanece").isEqualTo("OVERDUE");
        assertThat(subscriptionRepository.findByTenantId(t.getId()).orElseThrow().getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(acoesDesde(antes2)).containsExactly(AuditAction.TENANT_REACTIVATED);
        assertThat(tentarLogin("inad.admin@teste.local", SENHA).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void suspensaoManualTemMotivoMANUAL_eVencimentoNaoSuspendeSozinho() throws Exception {
        Tenant t = tenantComContrato("10.00");
        long id = gerar(t);
        fixar(id, "OPEN", hoje().minusDays(40), hoje().minusDays(35));
        billingService.refreshOverdue();
        assertThat(tenantRepository.findById(t.getId()).orElseThrow().getStatus()).as("vencida não suspende sozinha").isEqualTo("ATIVO");
        assertThat(postJson("/api/admin/tenants/" + t.getId() + "/suspend", Map.of(), sup()).getResponse().getStatus()).isEqualTo(200);
        assertThat(tenantRepository.findById(t.getId()).orElseThrow().getSuspensionReason()).isEqualTo(com.designart.model.SuspensionReason.MANUAL);
        assertThat(postJson("/api/admin/tenants/" + t.getId() + "/suspend-non-payment", Map.of(), sup()).getResponse().getStatus()).isEqualTo(409);
    }

    // ------------------------------------------------------------------ dashboard
    @Test
    void dashboardAgregaSemInterferirNoRestante() throws Exception {
        JsonNode antes = corpoJson(obter("/api/admin/billing/dashboard", sup()));
        Tenant aberta = tenantComContrato("100.00"), vencida = tenantComContrato("200.00"), paga = tenantComContrato("300.00");
        long ia = gerar(aberta), iv = gerar(vencida), ip = gerar(paga);
        fixar(ia, "OPEN", hoje().plusDays(20), hoje().plusDays(25));
        fixar(iv, "OPEN", hoje().minusDays(10), hoje().minusDays(5));
        assertThat(postJson("/api/admin/billing/invoices/" + ip + "/pay", Map.of("amount", new BigDecimal("300.00")), sup()).getResponse().getStatus()).isEqualTo(200);
        assertThat(postJson("/api/admin/tenants/" + vencida.getId() + "/suspend-non-payment", Map.of(), sup()).getResponse().getStatus()).isEqualTo(200);

        JsonNode depois = corpoJson(obter("/api/admin/billing/dashboard", sup()));
        assertThat(delta(antes, depois, "invoicesOpen")).isEqualTo(1);
        assertThat(delta(antes, depois, "invoicesOverdue")).isEqualTo(1);
        assertThat(delta(antes, depois, "invoicesBeyondGrace")).isEqualTo(1);
        assertThat(delta(antes, depois, "invoicesInGrace")).isZero();
        assertThat(delta(antes, depois, "invoicesPaid")).isEqualTo(1);
        assertThat(delta(antes, depois, "tenantsSuspended")).isEqualTo(1);
        assertThat(delta(antes, depois, "tenantsSuspendedNonPayment")).isEqualTo(1);
        assertThat(delta(antes, depois, "tenantsActive")).isEqualTo(2);
        assertThat(delta(antes, depois, "subscriptionsActive")).isEqualTo(3);
        assertThat(money(depois, "pendingAmount").subtract(money(antes, "pendingAmount"))).isEqualByComparingTo("100.00");
        assertThat(money(depois, "overdueAmount").subtract(money(antes, "overdueAmount"))).isEqualByComparingTo("200.00");
        assertThat(money(depois, "receivedInPeriod").subtract(money(antes, "receivedInPeriod"))).isEqualByComparingTo("300.00");
        assertThat(status(get("/api/admin/billing/dashboard?from=2026-05-02&to=2026-05-01"), sup())).isEqualTo(400);
        assertThat(status(get("/api/admin/billing/dashboard?from=2020-01-01&to=2026-05-01"), sup())).isEqualTo(400);
    }

    private static long delta(JsonNode antes, JsonNode depois, String campo) {
        return depois.get(campo).asLong() - antes.get(campo).asLong();
    }

    private static BigDecimal money(JsonNode n, String campo) {
        JsonNode m = n.get(campo).get("BRL");
        return m == null ? BigDecimal.ZERO : m.decimalValue();
    }

    // ------------------------------------------------------------------ acesso
    private static final List<java.util.function.Supplier<org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder>> ROTAS = List.of(
            () -> get("/api/admin/billing/invoices"),
            () -> get("/api/admin/billing/invoices/upcoming"),
            () -> get("/api/admin/billing/invoices/overdue"),
            () -> get("/api/admin/billing/invoices/in-grace"),
            () -> get("/api/admin/billing/invoices/beyond-grace"),
            () -> get("/api/admin/billing/invoices/1"),
            () -> get("/api/admin/billing/invoices/1/payments"),
            () -> post("/api/admin/billing/invoices/1/pay").contentType(MediaType.APPLICATION_JSON).content("{}"),
            () -> post("/api/admin/billing/invoices/1/cancel").contentType(MediaType.APPLICATION_JSON).content("{}"),
            () -> post("/api/admin/billing/refresh").contentType(MediaType.APPLICATION_JSON).content("{}"),
            () -> get("/api/admin/billing/payments"),
            () -> get("/api/admin/billing/dashboard"),
            () -> get("/api/admin/tenants/1/billing"),
            () -> get("/api/admin/tenants/1/billing-terms"),
            () -> put("/api/admin/tenants/1/billing-terms").contentType(MediaType.APPLICATION_JSON).content("{}"),
            () -> get("/api/admin/tenants/1/invoices"),
            () -> post("/api/admin/tenants/1/invoices").contentType(MediaType.APPLICATION_JSON).content("{}"),
            () -> post("/api/admin/tenants/1/suspend-non-payment").contentType(MediaType.APPLICATION_JSON).content("{}"));

    @Test
    void somenteSuperAdminAcessaOFinanceiro() throws Exception {
        Tenant t = novoTenant("ATIVO");
        criarUsuario("fin.tadmin@teste.local", Role.TENANT_ADMIN, t.getId());
        criarUsuario("fin.user@teste.local", Role.USER, t.getId());
        String admin = login("fin.tadmin@teste.local");
        String user = login("fin.user@teste.local");
        for (var rota : ROTAS) {
            assertThat(status(rota.get(), null)).as("anônimo " + rota.get()).isEqualTo(401);
            assertThat(status(rota.get(), admin)).as("TENANT_ADMIN " + rota.get()).isEqualTo(403);
            assertThat(status(rota.get(), user)).as("USER " + rota.get()).isEqualTo(403);
        }
        assertThat(status(get("/api/admin/billing/dashboard"), sup())).isEqualTo(200);
    }

    @Test
    void faturasDeUmTenantNaoVazamParaEndpointsDeNegocio() throws Exception {
        Tenant t = tenantComContrato("10.00");
        gerar(t);
        criarUsuario("fin.neg@teste.local", Role.TENANT_ADMIN, t.getId());
        String jwt = login("fin.neg@teste.local");
        for (String rota : List.of("/api/admin/billing/invoices", "/api/admin/tenants/" + t.getId() + "/billing")) {
            assertThat(status(get(rota), jwt)).isEqualTo(403);
        }
    }

    // ------------------------------------------------------------------ independência de conceitos
    @Test
    void cobrancaVencidaNaoAlteraAssinaturaNemTenantNemEntitlements() throws Exception {
        Tenant t = tenantComContrato("10.00");
        String ent = corpo(obter("/api/admin/tenants/" + t.getId() + "/entitlements", sup()));
        long id = gerar(t);
        fixar(id, "OPEN", hoje().minusDays(90), hoje().minusDays(85));
        assertThat(fatura(id).get("status").asText()).isEqualTo("OVERDUE");
        assertThat(subscriptionRepository.findByTenantId(t.getId()).orElseThrow().getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(tenantRepository.findById(t.getId()).orElseThrow().getStatus()).isEqualTo("ATIVO");
        assertThat(corpo(obter("/api/admin/tenants/" + t.getId() + "/entitlements", sup()))).isEqualTo(ent);
        assertThat(postJson("/api/admin/billing/invoices/" + id + "/cancel", Map.of(), sup()).getResponse().getStatus()).isEqualTo(200);
        assertThat(subscriptionRepository.findByTenantId(t.getId()).orElseThrow().getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void auditoriaDeTermosECancelamentoNaoVazaValores() throws Exception {
        Tenant t = tenantComContrato("777.77");
        long antes = maxAuditId();
        assertThat(putJson("/api/admin/tenants/" + t.getId() + "/billing-terms", Map.of("contractedAmount", new BigDecimal("888.88"), "graceDays", 7), sup())
                .getResponse().getStatus()).isEqualTo(200);
        long id = gerar(t);
        assertThat(postJson("/api/admin/billing/invoices/" + id + "/cancel", Map.of(), sup()).getResponse().getStatus()).isEqualTo(200);
        assertThat(acoesDesde(antes)).containsExactly(AuditAction.SUBSCRIPTION_CHANGED, AuditAction.INVOICE_CREATED, AuditAction.INVOICE_CANCELED);
        String dump = jdbc.queryForList("select * from audit_events where id > ?", antes).toString();
        assertThat(dump).doesNotContain("888").doesNotContain("777").contains("CONTRACTED_AMOUNT").contains("GRACE_DAYS");
        // termos idênticos não geram evento
        long antes2 = maxAuditId();
        assertThat(putJson("/api/admin/tenants/" + t.getId() + "/billing-terms", Map.of("contractedAmount", new BigDecimal("888.88")), sup()).getResponse().getStatus()).isEqualTo(200);
        assertThat(acoesDesde(antes2)).isEmpty();
    }
}
