package com.designart.admin;

import com.designart.billing.BillingRules;
import com.designart.billing.CollectionPhase;
import com.designart.billing.InvoiceStatus;
import com.designart.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Regras puras: bordas de vencimento/carência (datas fixas, sem relógio) e validação de dinheiro. */
class BillingRulesTest {

    private static final LocalDate DUE = LocalDate.of(2026, 3, 10);
    private static final LocalDate GRACE = LocalDate.of(2026, 3, 15);

    private CollectionPhase fase(InvoiceStatus s, LocalDate hoje) {
        return BillingRules.phase(s, DUE, GRACE, hoje, 7);
    }

    @Test
    void bordasDeVencimento() {
        assertThat(BillingRules.isPastDue(DUE, DUE.minusDays(1))).isFalse();
        assertThat(BillingRules.isPastDue(DUE, DUE)).as("no dia do vencimento").isFalse();
        assertThat(BillingRules.isPastDue(DUE, DUE.plusDays(1))).as("dia seguinte").isTrue();
    }

    @Test
    void bordasDaCarencia() {
        assertThat(BillingRules.isInGrace(DUE, GRACE, DUE)).as("não vencida ainda").isFalse();
        assertThat(BillingRules.isInGrace(DUE, GRACE, DUE.plusDays(1))).isTrue();
        assertThat(BillingRules.isInGrace(DUE, GRACE, GRACE)).as("último dia da carência").isTrue();
        assertThat(BillingRules.isInGrace(DUE, GRACE, GRACE.plusDays(1))).isFalse();
        assertThat(BillingRules.isBeyondGrace(GRACE, GRACE)).isFalse();
        assertThat(BillingRules.isBeyondGrace(GRACE, GRACE.plusDays(1))).isTrue();
    }

    @Test
    void carenciaZeroVenceEForaDaCarenciaNoMesmoDia() {
        assertThat(BillingRules.phase(InvoiceStatus.OPEN, DUE, DUE, DUE, 7)).isEqualTo(CollectionPhase.UPCOMING);
        assertThat(BillingRules.phase(InvoiceStatus.OVERDUE, DUE, DUE, DUE.plusDays(1), 7)).isEqualTo(CollectionPhase.BEYOND_GRACE);
    }

    @Test
    void janelaDeProximaAVencer() {
        assertThat(BillingRules.isUpcoming(DUE, DUE.minusDays(7), 7)).as("7 dias antes (inclusive)").isTrue();
        assertThat(BillingRules.isUpcoming(DUE, DUE.minusDays(8), 7)).isFalse();
        assertThat(BillingRules.isUpcoming(DUE, DUE, 7)).as("vence hoje").isTrue();
        assertThat(BillingRules.isUpcoming(DUE, DUE.plusDays(1), 7)).as("já vencida").isFalse();
    }

    @Test
    void fases() {
        assertThat(fase(InvoiceStatus.OPEN, DUE.minusDays(30))).isEqualTo(CollectionPhase.CURRENT);
        assertThat(fase(InvoiceStatus.OPEN, DUE.minusDays(3))).isEqualTo(CollectionPhase.UPCOMING);
        assertThat(fase(InvoiceStatus.OVERDUE, DUE.plusDays(2))).isEqualTo(CollectionPhase.IN_GRACE);
        assertThat(fase(InvoiceStatus.OVERDUE, GRACE.plusDays(1))).isEqualTo(CollectionPhase.BEYOND_GRACE);
        assertThat(fase(InvoiceStatus.PAID, GRACE.plusDays(30))).isEqualTo(CollectionPhase.NONE);
        assertThat(fase(InvoiceStatus.CANCELED, GRACE.plusDays(30))).isEqualTo(CollectionPhase.NONE);
    }

    @Test
    void competenciaEVencimentoAtravessamMesesEAnosBissextos() {
        assertThat(BillingRules.periodStart(LocalDate.of(2028, 2, 29))).isEqualTo(LocalDate.of(2028, 2, 1));
        assertThat(BillingRules.dueDate(LocalDate.of(2028, 2, 1), 28)).isEqualTo(LocalDate.of(2028, 2, 28));
        assertThat(BillingRules.dueDate(LocalDate.of(2027, 2, 1), 28)).isEqualTo(LocalDate.of(2027, 2, 28));
        assertThat(LocalDate.of(2026, 12, 28).plusDays(10)).as("carência atravessa o ano").isEqualTo(LocalDate.of(2027, 1, 7));
    }

    @Test
    void validacaoDeDinheiro() {
        assertThat(AdminRules.money(new BigDecimal("149.9"))).isEqualTo(new BigDecimal("149.90"));
        assertThat(AdminRules.money(new BigDecimal("10.000"))).isEqualTo(new BigDecimal("10.00"));
        for (String ruim : new String[]{"0", "0.00", "-0.01", "1.001", "10000000000"}) {
            assertThatThrownBy(() -> AdminRules.money(new BigDecimal(ruim))).as(ruim).isInstanceOf(InvalidRequestException.class);
        }
        assertThatThrownBy(() -> AdminRules.money(null)).isInstanceOf(InvalidRequestException.class);
        assertThat(AdminRules.externalRef("  PIX-123/a.b ")).isEqualTo("PIX-123/a.b");
        assertThat(AdminRules.externalRef(" ")).isNull();
        assertThatThrownBy(() -> AdminRules.externalRef("a b")).isInstanceOf(InvalidRequestException.class);
    }
}
