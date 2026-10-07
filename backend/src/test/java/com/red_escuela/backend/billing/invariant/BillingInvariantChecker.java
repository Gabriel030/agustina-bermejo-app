package com.red_escuela.backend.billing.invariant;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class BillingInvariantChecker {

    private final JdbcTemplate jdbc;
    private final long baselineEnrollmentId;
    private final java.util.Set<KnownInvariantViolation> tolerated = java.util.EnumSet.noneOf(KnownInvariantViolation.class);

    private BillingInvariantChecker(JdbcTemplate jdbc, long baselineEnrollmentId) {
        this.jdbc = jdbc;
        this.baselineEnrollmentId = baselineEnrollmentId;
    }

    public static BillingInvariantChecker startingNow(JdbcTemplate jdbc) {
        Long max = jdbc.queryForObject("select coalesce(max(id), 0) from program_enrollment", Long.class);
        return new BillingInvariantChecker(jdbc, Objects.requireNonNull(max));
    }

    public void tolerate(KnownInvariantViolation violation) {
        tolerated.add(violation);
    }

    public List<String> oneEnrollmentFeePerEnrollmentTemplateAndSlot() {
        return violations("I-05 enrollment fee issued twice for the same slot", """
                select 'enrollment ' || program_enrollment_id || ' template ' || template_id || ' slot ' || enrollment_fee_slot
                from invoice
                where program_enrollment_id > ? and fee_type = 'ENROLLMENT_FEE' and enrollment_fee_slot is not null and status <> 'CANCELLED'
                group by program_enrollment_id, template_id, enrollment_fee_slot
                having count(*) > 1
                """, baselineEnrollmentId);
    }

    public List<String> noNegativeAmountIsPersisted() {
        List<String> found = new ArrayList<>();
        found.addAll(violations("I-06 negative invoice base amount", "select 'invoice ' || id from invoice where base_amount < 0"));
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
                where i.program_enrollment_id > ? and a.amount_applied_currency <> i.base_amount_currency
                """, baselineEnrollmentId);
    }

    public List<String> invoicesAreNotDueBeforeTheyWereIssued() {
        return violations("I-14 invoice due before its issue date", """
                select 'invoice ' || id || ' due ' || due_date || ' created ' || created_at
                from invoice
                where program_enrollment_id > ? and due_date < cast(created_at as date)
                """, baselineEnrollmentId);
    }

    public List<String> installmentsStayWithinTheConfiguredCountAndSchoolYear() {
        List<String> found = new ArrayList<>(violations("I-15 more installments than configured", """
                select 'enrollment ' || i.program_enrollment_id || ' template ' || i.template_id
                from invoice i join program_year_billing_template t on t.id = i.template_id
                where i.program_enrollment_id > ? and i.fee_type = 'RECURRING_FEE' and i.status <> 'CANCELLED'
                group by i.program_enrollment_id, i.template_id, t.recurring_fee_invoices_count
                having count(*) > t.recurring_fee_invoices_count
                """, baselineEnrollmentId));
        if (!tolerated.contains(KnownInvariantViolation.UNDATED_TERMS_BILL_JANUARY_AND_FEBRUARY)) {
            found.addAll(violations("I-15 installment in January or February", """
                select 'invoice ' || id || ' period ' || period
                from invoice
                where program_enrollment_id > ? and fee_type = 'RECURRING_FEE' and extract(month from period) in (1, 2)
                """, baselineEnrollmentId));
        }
        return found;
    }

    public List<String> suspendedEnrollmentsReceiveNoNewInvoices() {
        return violations("I-16 invoice emitted for a suspended enrollment", """
                select 'invoice ' || i.id || ' enrollment ' || e.id
                from invoice i join program_enrollment e on e.id = i.program_enrollment_id
                where e.id > ? and e.billing_standing = 'SUSPENDED'
                  and i.status <> 'CANCELLED' and i.issue_reason = 'MONTHLY_JOB'
                  and i.created_at > e.billing_standing_updated_at
                """, baselineEnrollmentId);
    }

    public void assertAllBillingInvariantsHold() {
        List<String> all = new ArrayList<>();
        all.addAll(oneEnrollmentFeePerEnrollmentTemplateAndSlot());
        all.addAll(noNegativeAmountIsPersisted());
        all.addAll(allocationsHaveTheCurrencyOfTheirInvoice());
        all.addAll(invoicesAreNotDueBeforeTheyWereIssued());
        all.addAll(installmentsStayWithinTheConfiguredCountAndSchoolYear());
        all.addAll(suspendedEnrollmentsReceiveNoNewInvoices());
        if (!all.isEmpty()) {
            throw new AssertionError("Billing invariants violated:\n" + String.join("\n", all));
        }
    }

    private List<String> violations(String invariant, String sql, Object... args) {
        return jdbc.queryForList(sql, String.class, args).stream().map(row -> invariant + ": " + row).toList();
    }
}
