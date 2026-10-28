package com.red_escuela.backend.billing.support;

import com.red_escuela.backend.billing.payment.domain.PaymentReversalEffects;
import com.red_escuela.backend.billing.payment.domain.PaymentReversalEffects.InvoiceImpact;
import com.red_escuela.backend.billing.payment.domain.PaymentReversalEffects.LateFeeReinstatementImpact;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public final class ReversalEffectsParity {

    private ReversalEffectsParity() {
    }

    public record InvoiceState(String paymentStatus, BigDecimal outstanding) {
    }

    public record Snapshot(Map<Long, InvoiceState> invoices, Map<Long, BigDecimal> reversedAllocationsByInvoice, long lastLateFeeChargeId) {
    }

    public static Snapshot takeSnapshot(JdbcTemplate jdbc, Long accountId) {
        Map<Long, InvoiceState> invoices = new HashMap<>();
        jdbc.queryForList("select id, payment_status, outstanding_amount from invoice where student_current_account_id = ?", accountId)
                .forEach(row -> invoices.put(((Number) row.get("id")).longValue(), new InvoiceState((String) row.get("payment_status"), (BigDecimal) row.get("outstanding_amount"))));
        Map<Long, BigDecimal> reversed = new HashMap<>();
        jdbc.queryForList("select invoice_id, sum(amount) as total from payment_allocation where student_current_account_id = ? and reverses_allocation_id is not null "
                + "group by invoice_id", accountId).forEach(row -> reversed.put(((Number) row.get("invoice_id")).longValue(), (BigDecimal) row.get("total")));
        long lastCharge = jdbc.queryForObject("select coalesce(max(c.id), 0) from late_fee_charge c join invoice d on d.id = c.debit_note_id where d.student_current_account_id = ?",
                Long.class, accountId);
        return new Snapshot(invoices, reversed, lastCharge);
    }

    public static void assertThePredictionMatchesWhatTheRealOperationDid(JdbcTemplate jdbc, Long accountId, Snapshot before, PaymentReversalEffects predicted) {
        assertThat(predicted.calculated()).isTrue();
        Snapshot after = takeSnapshot(jdbc, accountId);
        Map<Long, InvoiceState> expected = new HashMap<>(before.invoices());
        for (InvoiceImpact reopened : predicted.reopenedInvoices()) {
            assertThat(before.invoices().get(reopened.invoiceId()).paymentStatus()).isEqualTo(reopened.statusBefore().name());
            assertThat(before.invoices().get(reopened.invoiceId()).outstanding()).isEqualByComparingTo(reopened.outstandingBefore().value().amount());
            expected.put(reopened.invoiceId(), new InvoiceState(reopened.statusAfter().name(), reopened.outstandingAfter().value().amount()));
        }
        for (InvoiceImpact settled : predicted.settledInvoices()) {
            expected.put(settled.invoiceId(), new InvoiceState(settled.statusAfter().name(), settled.outstandingAfter().value().amount()));
        }
        before.invoices().keySet().forEach(invoiceId -> {
            assertThat(after.invoices().get(invoiceId).paymentStatus()).as("payment status of invoice " + invoiceId).isEqualTo(expected.get(invoiceId).paymentStatus());
            assertThat(after.invoices().get(invoiceId).outstanding()).as("outstanding of invoice " + invoiceId).isEqualByComparingTo(expected.get(invoiceId).outstanding());
        });
        assertThatTheCreditReversedPerInvoiceMatches(before, after, predicted);
        assertThatTheReinstatedLateFeesMatch(jdbc, accountId, before, predicted);
    }

    private static void assertThatTheCreditReversedPerInvoiceMatches(Snapshot before, Snapshot after, PaymentReversalEffects predicted) {
        Map<Long, BigDecimal> predictedByInvoice = new HashMap<>();
        predicted.reopenedInvoices().forEach(reopened -> predictedByInvoice.put(reopened.invoiceId(), reopened.creditReversed().value().amount()));
        after.invoices().keySet().forEach(invoiceId -> {
            BigDecimal reversedByTheOperation = after.reversedAllocationsByInvoice().getOrDefault(invoiceId, BigDecimal.ZERO)
                    .subtract(before.reversedAllocationsByInvoice().getOrDefault(invoiceId, BigDecimal.ZERO));
            assertThat(reversedByTheOperation).as("credit reversed from invoice " + invoiceId).isEqualByComparingTo(predictedByInvoice.getOrDefault(invoiceId, BigDecimal.ZERO));
        });
    }

    private static void assertThatTheReinstatedLateFeesMatch(JdbcTemplate jdbc, Long accountId, Snapshot before, PaymentReversalEffects predicted) {
        List<Map<String, Object>> created = jdbc.queryForList("select c.origin_invoice_id, c.period_start, c.period_end, c.charged_days, d.total, d.payment_status "
                + "from late_fee_charge c join invoice d on d.id = c.debit_note_id where d.student_current_account_id = ? and c.id > ? and c.reinstates_correction_id is not null order by c.id", accountId,
                before.lastLateFeeChargeId());
        List<String> actual = new ArrayList<>();
        for (Map<String, Object> row : created) {
            actual.add(row.get("origin_invoice_id") + "|" + row.get("period_start") + "|" + row.get("period_end") + "|" + row.get("charged_days") + "|"
                    + ((BigDecimal) row.get("total")).setScale(2) + "|" + "PAID".equals(row.get("payment_status")));
        }
        List<String> expected = new ArrayList<>();
        for (LateFeeReinstatementImpact lateFee : predicted.lateFeeReinstatements()) {
            expected.add(lateFee.originInvoiceId() + "|" + LocalDate.parse(lateFee.firstDay().toString()) + "|" + lateFee.lastDay() + "|" + lateFee.chargedDays() + "|"
                    + lateFee.amount().value().amount().setScale(2) + "|" + lateFee.settledByAccountCredit());
        }
        assertThat(actual).isEqualTo(expected);
        assertThat(created).as("reinstatement debit notes created by the operation").hasSize(predicted.lateFeeReinstatements().size());
    }
}
