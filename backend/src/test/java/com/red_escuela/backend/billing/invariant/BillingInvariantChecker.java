package com.red_escuela.backend.billing.invariant;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class BillingInvariantChecker {

    private final JdbcTemplate jdbc;
    private final long baselineEnrollmentId;
    private final long baselineInvoiceId;
    private final long baselineAllocationId;

    private BillingInvariantChecker(JdbcTemplate jdbc, long baselineEnrollmentId, long baselineInvoiceId, long baselineAllocationId) {
        this.jdbc = jdbc;
        this.baselineEnrollmentId = baselineEnrollmentId;
        this.baselineInvoiceId = baselineInvoiceId;
        this.baselineAllocationId = baselineAllocationId;
    }

    public static BillingInvariantChecker startingNow(JdbcTemplate jdbc) {
        Long max = jdbc.queryForObject("select coalesce(max(id), 0) from program_enrollment", Long.class);
        Long maxInvoice = jdbc.queryForObject("select coalesce(max(id), 0) from invoice", Long.class);
        Long maxAllocation = jdbc.queryForObject("select coalesce(max(id), 0) from payment_allocation", Long.class);
        return new BillingInvariantChecker(jdbc, Objects.requireNonNull(max), maxInvoice, maxAllocation);
    }

    public List<String> oneEnrollmentFeePerEnrollmentTemplateAndSlot() {
        return violations("I-05 enrollment fee issued twice for the same slot", """
                select 'enrollment ' || program_enrollment_id || ' template ' || template_id || ' slot ' || enrollment_fee_slot
                from invoice_line
                where program_enrollment_id > ? and fee_type = 'ENROLLMENT_FEE' and enrollment_fee_slot is not null and charge_key_released_at is null
                group by program_enrollment_id, template_id, enrollment_fee_slot
                having count(*) > 1
                """, baselineEnrollmentId);
    }

    public List<String> noNegativeAmountIsPersisted() {
        List<String> found = new ArrayList<>();
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
                where a.id > ? and a.amount_applied_currency <> i.currency
                """, baselineAllocationId);
    }

    public List<String> invoicesAreNotDueBeforeTheyWereIssued() {
        return violations("I-14 invoice due before its issue date", """
                select 'invoice ' || id || ' due ' || due_date || ' issued ' || issue_date
                from invoice
                where id > ? and due_date < issue_date
                """, baselineInvoiceId);
    }

    public List<String> installmentsStayWithinTheConfiguredCountAndSchoolYear() {
        List<String> found = new ArrayList<>(violations("I-15 more installments than configured", """
                select 'enrollment ' || i.program_enrollment_id || ' template ' || i.template_id
                from invoice_line i join program_year_billing_template t on t.id = i.template_id
                where i.program_enrollment_id > ? and i.fee_type = 'MONTHLY_INSTALLMENT' and i.charge_key_released_at is null
                group by i.program_enrollment_id, i.template_id, t.recurring_fee_invoices_count
                having count(*) > t.recurring_fee_invoices_count
                """, baselineEnrollmentId));
        found.addAll(violations("I-15 installment in January or February", """
                select 'invoice ' || id || ' period ' || period
                from invoice_line
                where program_enrollment_id > ? and fee_type = 'MONTHLY_INSTALLMENT' and extract(month from period) in (1, 2)
                """, baselineEnrollmentId));
        return found;
    }

    public List<String> suspendedEnrollmentsReceiveNoNewInvoices() {
        return violations("I-16 invoice emitted for a suspended enrollment", """
                select 'invoice ' || i.id || ' enrollment ' || e.id
                from invoice i join invoice_line l on l.invoice_id = i.id join program_enrollment e on e.id = l.program_enrollment_id
                where e.id > ? and e.payment_status = 'SUSPENDED'
                  and i.issuing_reason = 'MONTHLY_RUN'
                  and i.created_at > e.payment_status_updated_at
                """, baselineEnrollmentId);
    }

    public List<String> aChargeKeyIsHeldByAtMostOneLiveLine() {
        return violations("I-05 charge key held by more than one live invoice line", """
                select 'charge key ' || l.charge_key
                from invoice_line l join invoice i on i.id = l.invoice_id
                where l.charge_key_released_at is null and i.document_status <> 'DISCARDED'
                  and (i.payment_status is null or i.payment_status <> 'CANCELLED')
                group by l.charge_key
                having count(*) > 1
                """);
    }

    public List<String> noNegativeAmountIsPersistedInTheV2Tables() {
        List<String> found = new ArrayList<>();
        String[][] columns = {
                {"invoice", "lines_total"}, {"invoice", "discounts_total"}, {"invoice", "total"}, {"invoice", "paid_amount"},
                {"invoice", "credited_amount"}, {"invoice", "outstanding_amount"}, {"invoice_line", "amount"},
                {"invoice_line", "discount_amount"}, {"invoice_line", "credited_amount"}, {"invoice_discount", "base_amount"},
                {"invoice_discount", "discount_amount"}, {"student_current_account", "balance_amount"},
                {"student_current_account", "unallocated_credit_amount"}, {"current_account_entry", "amount"},
                {"current_account_entry", "running_balance_amount"}};
        for (String[] column : columns) {
            found.addAll(violations("I-06 negative amount in " + column[0] + "." + column[1],
                    "select '" + column[0] + " ' || id from " + column[0] + " where " + column[1] + " < 0"));
        }
        return found;
    }

    public List<String> invoiceTotalsMatchTheirLinesAndDiscounts() {
        return violations("I-07 invoice totals do not match their lines and discounts", """
                select 'invoice ' || i.id
                from invoice i
                where i.lines_total <> coalesce((select sum(l.amount) from invoice_line l where l.invoice_id = i.id), 0)
                   or i.discounts_total <> coalesce((select sum(d.discount_amount) from invoice_line l
                        join invoice_discount d on d.invoice_line_id = l.id where l.invoice_id = i.id), 0)
                   or exists (select 1 from invoice_line l left join invoice_discount d on d.invoice_line_id = l.id
                        where l.invoice_id = i.id and l.discount_amount <> coalesce(d.discount_amount, 0))
                   or i.total <> i.lines_total - i.discounts_total
                   or i.total < 0
                """);
    }

    public List<String> paymentStatusIsConsistentWithAmounts() {
        return violations("I-08 payment status inconsistent with the amounts", """
                select 'invoice ' || id || ' ' || payment_status
                from invoice
                where (payment_status in ('PENDING', 'PAYMENT_UNDER_REVIEW', 'OVERDUE') and not (paid_amount = 0 and outstanding_amount > 0))
                   or (payment_status = 'PAID' and outstanding_amount <> 0)
                   or (payment_status = 'CANCELLED' and not (paid_amount = 0 and outstanding_amount = 0 and credited_amount = total))
                   or (payment_status is null and (paid_amount <> 0 or credited_amount <> 0))
                """);
    }

    public List<String> thereIsNoWaivedNorPartiallyPaidStatus() {
        List<String> found = new ArrayList<>(violations("I-12 forbidden payment status in an invoice", """
                select 'invoice ' || id || ' ' || payment_status
                from invoice where payment_status in ('WAIVED', 'PARTIALLY_PAID')
                """));
        found.addAll(violations("I-12 forbidden payment status in the history", """
                select 'change ' || id || ' ' || to_status
                from invoice_status_change
                where status_kind = 'PAYMENT' and (to_status in ('WAIVED', 'PARTIALLY_PAID') or from_status in ('WAIVED', 'PARTIALLY_PAID'))
                """));
        return found;
    }

    public List<String> v2RowsHaveTheCurrencyOfTheirInvoiceOrAccount() {
        List<String> found = new ArrayList<>();
        found.addAll(violations("I-13 invoice line in a different currency than its invoice", """
                select 'line ' || l.id from invoice_line l join invoice i on i.id = l.invoice_id where l.currency <> i.currency
                """));
        found.addAll(violations("I-13 invoice discount in a different currency than its line", """
                select 'discount ' || d.id from invoice_discount d join invoice_line l on l.id = d.invoice_line_id where d.currency <> l.currency
                """));
        found.addAll(violations("I-13 invoice in a different currency than its account", """
                select 'invoice ' || i.id from invoice i join student_current_account a on a.id = i.student_current_account_id
                where i.currency <> a.currency
                """));
        return found;
    }

    public List<String> theLatestStatusChangeMatchesTheCurrentStatus() {
        List<String> found = new ArrayList<>();
        found.addAll(violations("I-17 document history does not end in the current document status", """
                select 'invoice ' || i.id
                from invoice i
                where coalesce((select c.to_status from invoice_status_change c
                        where c.invoice_id = i.id and c.status_kind = 'DOCUMENT' order by c.id desc limit 1), '') <> i.document_status
                """));
        found.addAll(violations("I-17 payment history does not end in the current payment status", """
                select 'invoice ' || i.id
                from invoice i
                where coalesce((select c.to_status from invoice_status_change c
                        where c.invoice_id = i.id and c.status_kind = 'PAYMENT' order by c.id desc limit 1), '')
                      <> coalesce(i.payment_status, '')
                """));
        found.addAll(violations("I-17 invoice without its initial history row", """
                select 'invoice ' || i.id
                from invoice i
                where not exists (select 1 from invoice_status_change c
                        where c.invoice_id = i.id and c.status_kind = 'DOCUMENT' and c.from_status is null and c.to_status = 'DRAFT')
                """));
        return found;
    }

    public List<String> accountBalanceEqualsCreditsMinusDebits() {
        return violations("I-01 account balance differs from credits minus debits", """
                select 'account ' || a.id
                from student_current_account a
                where (case a.balance_side when 'OWES' then -a.balance_amount when 'IN_FAVOR' then a.balance_amount else 0 end)
                    <> coalesce((select sum(case e.direction when 'CREDIT' then e.amount else -e.amount end)
                                 from current_account_entry e where e.student_current_account_id = a.id), 0)
                """);
    }

    public List<String> runningBalancesFollowTheEntrySequence() {
        List<String> found = new ArrayList<>(violations("I-02 running balance is not the previous one moved by the amount", """
                select 'entry ' || id || ' account ' || student_current_account_id
                from (select e.id, e.student_current_account_id,
                             (case e.running_balance_side when 'OWES' then -e.running_balance_amount else e.running_balance_amount end) as running,
                             coalesce(lag(case e.running_balance_side when 'OWES' then -e.running_balance_amount else e.running_balance_amount end)
                                 over (partition by e.student_current_account_id order by e.sequence_number), 0) as previous,
                             (case e.direction when 'CREDIT' then e.amount else -e.amount end) as moved
                      from current_account_entry e) steps
                where running <> previous + moved
                """));
        found.addAll(violations("I-02 sequence is not contiguous or last_entry_sequence differs from the maximum", """
                select 'account ' || a.id
                from student_current_account a
                where a.last_entry_sequence <> coalesce((select max(e.sequence_number) from current_account_entry e where e.student_current_account_id = a.id), 0)
                   or a.last_entry_sequence <> (select count(*) from current_account_entry e where e.student_current_account_id = a.id)
                """));
        return found;
    }

    public List<String> accountBalanceIsTheUnallocatedCreditMinusTheOutstandingInvoices() {
        List<String> found = new ArrayList<>(violations("I-19 balance differs from unallocated credit minus outstanding", """
                select 'account ' || a.id
                from student_current_account a
                where a.last_entry_sequence > 0
                  and (case a.balance_side when 'OWES' then -a.balance_amount when 'IN_FAVOR' then a.balance_amount else 0 end)
                    <> a.unallocated_credit_amount - coalesce((select sum(i.outstanding_amount) from invoice i
                        where i.student_current_account_id = a.id and i.document_status = 'ISSUED'), 0)
                """));
        found.addAll(violations("I-19 issued invoice without exactly one debit (or a zero total invoice with one)", """
                select 'invoice ' || i.id
                from invoice i
                where i.document_status = 'ISSUED'
                  and exists (select 1 from student_current_account a where a.id = i.student_current_account_id and a.last_entry_sequence > 0)
                  and (select count(*) from current_account_entry e where e.invoice_id = i.id) <> (case when i.total > 0 then 1 else 0 end)
                """));
        return found;
    }

    public void assertAllBillingInvariantsHold() {
        List<String> all = new ArrayList<>();
        all.addAll(oneEnrollmentFeePerEnrollmentTemplateAndSlot());
        all.addAll(noNegativeAmountIsPersisted());
        all.addAll(allocationsHaveTheCurrencyOfTheirInvoice());
        all.addAll(invoicesAreNotDueBeforeTheyWereIssued());
        all.addAll(installmentsStayWithinTheConfiguredCountAndSchoolYear());
        all.addAll(suspendedEnrollmentsReceiveNoNewInvoices());
        all.addAll(aChargeKeyIsHeldByAtMostOneLiveLine());
        all.addAll(noNegativeAmountIsPersistedInTheV2Tables());
        all.addAll(invoiceTotalsMatchTheirLinesAndDiscounts());
        all.addAll(paymentStatusIsConsistentWithAmounts());
        all.addAll(thereIsNoWaivedNorPartiallyPaidStatus());
        all.addAll(v2RowsHaveTheCurrencyOfTheirInvoiceOrAccount());
        all.addAll(theLatestStatusChangeMatchesTheCurrentStatus());
        all.addAll(accountBalanceEqualsCreditsMinusDebits());
        all.addAll(runningBalancesFollowTheEntrySequence());
        all.addAll(accountBalanceIsTheUnallocatedCreditMinusTheOutstandingInvoices());
        if (!all.isEmpty()) {
            throw new AssertionError("Billing invariants violated:\n" + String.join("\n", all));
        }
    }

    private List<String> violations(String invariant, String sql, Object... args) {
        return jdbc.queryForList(sql, String.class, args).stream().map(row -> invariant + ": " + row).toList();
    }
}
