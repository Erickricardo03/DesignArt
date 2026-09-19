package com.designart.billing;

import java.time.LocalDate;

/**
 * Regras PURAS (sem relógio, sem banco) de datas e fases de cobrança. Todas as datas são dias UTC
 * ({@code LocalDate}); quem chama passa "hoje" vindo do Clock injetado.
 *
 * <h3>Bordas</h3>
 * <ul>
 *   <li>vencimento D: no próprio dia D a cobrança AINDA está em aberto; vencida a partir de D+1;</li>
 *   <li>carência G = D + grace_days (inclusive): em carência até G; fora da carência a partir de G+1;</li>
 *   <li>"próxima a vencer": OPEN com hoje &lt;= D e D - hoje &lt;= janela (inclusive nas duas pontas).</li>
 * </ul>
 */
public final class BillingRules {

    /** Janela padrão (dias) de "próxima a vencer". */
    public static final int DEFAULT_UPCOMING_DAYS = 7;
    public static final int MAX_UPCOMING_DAYS = 90;

    private BillingRules() {
    }

    /** Vencida no dia {@code today}? (estritamente depois do vencimento) */
    public static boolean isPastDue(LocalDate dueDate, LocalDate today) {
        return today.isAfter(dueDate);
    }

    /** Dentro da carência: vencida e hoje &lt;= fim da carência. */
    public static boolean isInGrace(LocalDate dueDate, LocalDate graceEndsOn, LocalDate today) {
        return isPastDue(dueDate, today) && !today.isAfter(graceEndsOn);
    }

    /** Fora da carência: hoje depois do fim da carência. */
    public static boolean isBeyondGrace(LocalDate graceEndsOn, LocalDate today) {
        return today.isAfter(graceEndsOn);
    }

    public static boolean isUpcoming(LocalDate dueDate, LocalDate today, int windowDays) {
        return !today.isAfter(dueDate) && !dueDate.isAfter(today.plusDays(windowDays));
    }

    /** Fase de cobrança em {@code today}; usa as datas (não confia só no status persistido). */
    public static CollectionPhase phase(InvoiceStatus status, LocalDate dueDate, LocalDate graceEndsOn,
                                        LocalDate today, int upcomingWindowDays) {
        if (status == null || !status.isUnpaid()) {
            return CollectionPhase.NONE;
        }
        if (isBeyondGrace(graceEndsOn, today)) {
            return CollectionPhase.BEYOND_GRACE;
        }
        if (isPastDue(dueDate, today)) {
            return CollectionPhase.IN_GRACE;
        }
        return isUpcoming(dueDate, today, upcomingWindowDays) ? CollectionPhase.UPCOMING : CollectionPhase.CURRENT;
    }

    /** Competência: primeiro dia do mês. */
    public static LocalDate periodStart(LocalDate anyDayOfMonth) {
        return anyDayOfMonth.withDayOfMonth(1);
    }

    /** Vencimento da competência: o {@code billingDay} (1..28) do mês da competência. */
    public static LocalDate dueDate(LocalDate referencePeriod, int billingDay) {
        return referencePeriod.withDayOfMonth(billingDay);
    }
}
