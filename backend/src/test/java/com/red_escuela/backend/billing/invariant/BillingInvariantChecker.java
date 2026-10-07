package com.red_escuela.backend.billing.invariant;

import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class BillingInvariantChecker {

    private final JdbcTemplate jdbc;
    private final long baselineInvoiceId;
    private long lastCheckedInvoiceId;

    private BillingInvariantChecker(JdbcTemplate jdbc, long baselineInvoiceId) {
        this.jdbc = jdbc;
        this.baselineInvoiceId = baselineInvoiceId;
        this.lastCheckedInvoiceId = baselineInvoiceId;
    }

    public static BillingInvariantChecker startingNow(JdbcTemplate jdbc) {
        Long max = jdbc.queryForObject("select coalesce(max(id), 0) from invoice", Long.class);
        return new BillingInvariantChecker(jdbc, Objects.requireNonNull(max));
    }

    public List<String> oneEnrollmentFeePerEnrollmentTemplateAndSlot() {
        return violations("I-05 enrollment fee issued twice for the same slot", """
                select 'enrollment ' || program_enrollment_id || ' template ' || template_id || ' slot ' || enrollment_fee_slot
                from invoice
                where id > ? and fee_type = 'ENROLLMENT_FEE' and enrollment_fee_slot is not null and status <> 'CANCELLED'
                group by program_enrollment_id, template_id, enrollment_fee_slot
                having count(*) > 1
                """, baselineInvoiceId);
    }

    public List<String> noNegativeAmountIsPersisted() {
        List<String> found = new ArrayList<>();
        found.addAll(violations("I-06 negative invoice base amount", "select 'invoice ' || id from invoice where id > ? and base_amount < 0", baselineInvoiceId));
        found.addAll(violations("I-06 negative adjustment", "select 'adjustment ' || id from invoice_adjustment where resolved_base < 0"));
        found.addAll(violations("I-06 negative allocation", "select 'allocation ' || id from payment_allocation where amount_applied < 0"));
        found.addAll(violations("I-06 negative payment", "select 'payment ' || id from payment where amount < 0"));
        found.addAll(violations("I-06 negative credit note", "select 'credit note ' || id from credit_note where amount < 0"));
        found.addAll(violations("I-06 negative credit application", "select 'credit application ' || id from credit_application where amount_applied < 0"));
        found.addAll(violations("I-06 negative price", "select 'price revision ' || id from price_revision where monthly_amount < 0"));
        return found;
    }

    public List<String> allocationsHaveTheCurrencyOfTheirInvoice() {
        return violations("I-13 allocation in a different currency than its invoice", """
                select 'allocation ' || a.id
                from payment_allocation a join invoice i on i.id = a.invoice_id
                where i.id > ? and a.amount_applied_currency <> i.base_amount_currency
                """, baselineInvoiceId);
    }

    public List<String> newInvoicesAreNotDueBeforeToday(LocalDate today) {
        return violations("I-14 invoice due before its issue date", """
                select 'invoice ' || id || ' due ' || due_date
                from invoice
                where id > ? and due_date < ?
                """, lastCheckedInvoiceId, java.sql.Date.valueOf(today));
    }

    public List<String> installmentsStayWithinTheConfiguredCountAndSchoolYear() {
        List<String> found = new ArrayList<>(violations("I-15 more installments than configured", """
                select 'enrollment ' || i.program_enrollment_id || ' template ' || i.template_id
                from invoice i join program_year_billing_template t on t.id = i.template_id
                where i.id > ? and i.fee_type = 'RECURRING_FEE' and i.status <> 'CANCELLED'
                group by i.program_enrollment_id, i.template_id, t.recurring_fee_invoices_count
                having count(*) > t.recurring_fee_invoices_count
                """, baselineInvoiceId));
        found.addAll(violations("I-15 installment in January or February", """
                select 'invoice ' || id || ' period ' || period
                from invoice
                where id > ? and fee_type = 'RECURRING_FEE' and extract(month from period) in (1, 2)
                """, baselineInvoiceId));
        return found;
    }

    public List<String> suspendedEnrollmentsReceiveNoNewInvoices() {
        return violations("I-16 invoice emitted for a suspended enrollment", """
                select 'invoice ' || i.id || ' enrollment ' || e.id
                from invoice i join program_enrollment e on e.id = i.program_enrollment_id
                where i.id > ? and e.billing_standing = 'SUSPENDED'
                """, lastCheckedInvoiceId);
    }

    public void assertAllBillingInvariantsHold(LocalDate today) {
        List<String> all = new ArrayList<>();
        all.addAll(oneEnrollmentFeePerEnrollmentTemplateAndSlot());
        all.addAll(noNegativeAmountIsPersisted());
        all.addAll(allocationsHaveTheCurrencyOfTheirInvoice());
        all.addAll(newInvoicesAreNotDueBeforeToday(today));
        all.addAll(installmentsStayWithinTheConfiguredCountAndSchoolYear());
        all.addAll(suspendedEnrollmentsReceiveNoNewInvoices());
        Long max = jdbc.queryForObject("select coalesce(max(id), 0) from invoice", Long.class);
        lastCheckedInvoiceId = Math.max(lastCheckedInvoiceId, Objects.requireNonNull(max));
        if (!all.isEmpty()) {
            throw new AssertionError("Billing invariants violated:\n" + String.join("\n", all));
        }
    }

    private List<String> violations(String invariant, String sql, Object... args) {
        return jdbc.queryForList(sql, String.class, args).stream().map(row -> invariant + ": " + row).toList();
    }
}
