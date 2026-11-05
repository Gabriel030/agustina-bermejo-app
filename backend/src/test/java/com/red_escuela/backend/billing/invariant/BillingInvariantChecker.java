package com.red_escuela.backend.billing.invariant;

import com.red_escuela.backend.billing.run.domain.AccountConsistencyCheck;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class BillingInvariantChecker {

    private final JdbcTemplate jdbc;
    private final long baselineEnrollmentId;
    private final long baselineInvoiceId;
    private final long baselineAllocationId;
    private final long baselineAccountId;
    private final long baselineEntryId;
    private final long baselineReminderId;
    private final long baselineDeliveryId;

    private static final String ACCOUNT_IS_NEW_OR_HAS_NEW_ENTRIES =
            "(a.id > ? or exists (select 1 from current_account_entry x where x.student_current_account_id = a.id and x.id > ?))";

    private static final String INVOICE_IS_NEW_OR_ITS_ACCOUNT_HAS_NEW_ENTRIES =
            "(i.id > ? or exists (select 1 from student_current_account a where a.id = i.student_current_account_id and "
                    + ACCOUNT_IS_NEW_OR_HAS_NEW_ENTRIES + "))";

    private BillingInvariantChecker(JdbcTemplate jdbc, long baselineEnrollmentId, long baselineInvoiceId, long baselineAllocationId, long baselineAccountId, long baselineEntryId, long baselineReminderId,
                                      long baselineDeliveryId) {
        this.jdbc = jdbc;
        this.baselineEnrollmentId = baselineEnrollmentId;
        this.baselineInvoiceId = baselineInvoiceId;
        this.baselineAllocationId = baselineAllocationId;
        this.baselineAccountId = baselineAccountId;
        this.baselineEntryId = baselineEntryId;
        this.baselineReminderId = baselineReminderId;
        this.baselineDeliveryId = baselineDeliveryId;
    }

    public static BillingInvariantChecker startingNow(JdbcTemplate jdbc) {
        Long max = jdbc.queryForObject("select coalesce(max(id), 0) from program_enrollment", Long.class);
        Long maxInvoice = jdbc.queryForObject("select coalesce(max(id), 0) from invoice", Long.class);
        Long maxAllocation = jdbc.queryForObject("select coalesce(max(id), 0) from payment_allocation", Long.class);
        Long maxAccount = jdbc.queryForObject("select coalesce(max(id), 0) from student_current_account", Long.class);
        Long maxEntry = jdbc.queryForObject("select coalesce(max(id), 0) from current_account_entry", Long.class);
        Long maxReminder = jdbc.queryForObject("select coalesce(max(id), 0) from collection_reminder", Long.class);
        Long maxDelivery = jdbc.queryForObject("select coalesce(max(id), 0) from notification_delivery", Long.class);
        return new BillingInvariantChecker(jdbc, Objects.requireNonNull(max), maxInvoice, maxAllocation, maxAccount, maxEntry, maxReminder, maxDelivery);
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
        found.addAll(violations("I-06 negative allocation", "select 'allocation ' || id from payment_allocation where amount < 0"));
        found.addAll(violations("I-06 negative payment", "select 'payment ' || id from payment where amount < 0"));
        found.addAll(violations("I-06 negative credit note", "select 'credit note ' || id from credit_note where total_amount < 0"));
        found.addAll(violations("I-06 negative price", "select 'price version ' || id from price_version where amount < 0"));
        return found;
    }

    public List<String> allocationsHaveTheCurrencyOfTheirInvoice() {
        return violations("I-13 allocation in a different currency than its invoice", """
                select 'allocation ' || a.id
                from payment_allocation a join invoice i on i.id = a.invoice_id
                where a.id > ? and a.amount_currency <> i.currency
                """, baselineAllocationId);
    }

    public List<String> invoicesAreNotDueBeforeTheyWereIssued() {
        return violations("I-14 invoice due before its issue date", """
                select 'invoice ' || i.id || ' due ' || i.due_date || ' issued ' || i.issue_date
                from invoice i
                where @INVOICES@
                  and i.due_date < i.issue_date
                """, baselineInvoiceId, baselineAccountId, baselineEntryId);
    }

    public List<String> installmentsStayWithinTheConfiguredCountAndSchoolYear() {
        List<String> found = new ArrayList<>(violations("I-15 more installments than configured", """
                select 'enrollment ' || i.program_enrollment_id || ' template ' || i.template_id
                from invoice_line i join program_year_billing_template t on t.id = i.template_id
                join lateral (select (v.config_values ->> 'recurringFeeInvoicesCount')::int as installments from configuration_version v
                              where v.scope = 'YEAR_TERMS' and v.topic_key = 'YEAR_TERMS:P' || t.program_id || ':T' || t.term_id
                                and v.status in ('SCHEDULED', 'EFFECTIVE', 'SUPERSEDED')
                              order by v.effective_from desc, v.decided_at desc, v.id desc limit 1) c on true
                where i.program_enrollment_id > ? and i.fee_type = 'MONTHLY_INSTALLMENT' and i.charge_key_released_at is null
                group by i.program_enrollment_id, i.template_id, c.installments
                having count(*) > c.installments
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
                join enrollment_payment_standing s on s.program_enrollment_id = e.id
                where e.id > ? and s.status = 'SUSPENDED'
                  and i.issuing_reason = 'MONTHLY_RUN'
                  and i.created_at > (select max(c.occurred_at) from enrollment_payment_status_change c where c.standing_id = s.id)
                """, baselineEnrollmentId);
    }

    public List<String> aChargeKeyIsHeldByAtMostOneLiveLine() {
        return violationsOf(AccountConsistencyCheck.CHARGE_KEY_HELD_BY_MORE_THAN_ONE_LIVE_LINE);
    }

    public List<String> noNegativeAmountIsPersistedInTheV2Tables() {
        List<String> found = new ArrayList<>();
        String[][] columns = {
                {"invoice", "lines_total"}, {"invoice", "discounts_total"}, {"invoice", "total"}, {"invoice", "paid_amount"},
                {"invoice", "credited_amount"}, {"invoice", "outstanding_amount"}, {"invoice_line", "amount"},
                {"invoice_line", "discount_amount"}, {"invoice_line", "credited_amount"}, {"invoice_discount", "base_amount"},
                {"invoice_discount", "discount_amount"}, {"student_current_account", "balance_amount"},
                {"student_current_account", "unallocated_credit_amount"}, {"current_account_entry", "amount"},
                {"current_account_entry", "running_balance_amount"}, {"refund", "amount"}};
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
                where @INVOICES@ and (i.lines_total <> coalesce((select sum(l.amount) from invoice_line l where l.invoice_id = i.id), 0)
                   or i.discounts_total <> coalesce((select sum(d.discount_amount) from invoice_line l
                        join invoice_discount d on d.invoice_line_id = l.id where l.invoice_id = i.id), 0)
                   or exists (select 1 from invoice_line l left join invoice_discount d on d.invoice_line_id = l.id
                        where l.invoice_id = i.id and l.discount_amount <> coalesce(d.discount_amount, 0))
                   or i.total <> i.lines_total - i.discounts_total
                   or i.total < 0)
                """, baselineInvoiceId, baselineAccountId, baselineEntryId);
    }

    public List<String> paymentStatusIsConsistentWithAmounts() {
        return violationsOf(AccountConsistencyCheck.PAYMENT_STATUS_DIFFERS_FROM_AMOUNTS, baselineInvoiceId, baselineAccountId, baselineEntryId);
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
                where @INVOICES@ and coalesce((select c.to_status from invoice_status_change c
                        where c.invoice_id = i.id and c.status_kind = 'DOCUMENT' order by c.id desc limit 1), '') <> i.document_status
                """, baselineInvoiceId, baselineAccountId, baselineEntryId));
        found.addAll(violations("I-17 payment history does not end in the current payment status", """
                select 'invoice ' || i.id
                from invoice i
                where @INVOICES@ and coalesce((select c.to_status from invoice_status_change c
                        where c.invoice_id = i.id and c.status_kind = 'PAYMENT' order by c.id desc limit 1), '')
                      <> coalesce(i.payment_status, '')
                """, baselineInvoiceId, baselineAccountId, baselineEntryId));
        found.addAll(violations("I-17 invoice without its initial history row", """
                select 'invoice ' || i.id
                from invoice i
                where @INVOICES@ and not exists (select 1 from invoice_status_change c
                        where c.invoice_id = i.id and c.status_kind = 'DOCUMENT' and c.from_status is null and c.to_status = 'DRAFT')
                """, baselineInvoiceId, baselineAccountId, baselineEntryId));
        return found;
    }

    public List<String> refundedAmountOfEveryPaymentEqualsItsCompletedRefunds() {
        return violations("I-21 refunded amount of a payment differs from the sum of its completed refunds", """
                select 'payment ' || p.id
                from payment p join student_current_account a on a.id = p.student_current_account_id
                where @ACCOUNTS@ and exists (select 1 from refund r where r.payment_id = p.id)
                  and p.refunded_amount <> coalesce((select sum(r.amount) from refund r where r.payment_id = p.id and r.status = 'COMPLETED'), 0)
                """, baselineAccountId, baselineEntryId);
    }

    public List<String> everyCompletedRefundHasExactlyItsRefundPaidDebit() {
        return violations("I-22 completed refund without exactly one refund debit of its amount, or a refund debit of a refund that is not completed", """
                select 'refund ' || r.id
                from refund r join student_current_account a on a.id = r.student_current_account_id
                where @ACCOUNTS@ and (select count(*) from current_account_entry e
                        where e.refund_id = r.id and e.entry_type = 'REFUND_PAID' and e.amount = r.amount)
                      <> case when r.status = 'COMPLETED' then 1 else 0 end
                """, baselineAccountId, baselineEntryId);
    }

    public List<String> theUnallocatedCreditCoversTheAmountReservedByPendingRefunds() {
        return violations("I-23 unallocated credit below the amount reserved by requested or approved refunds", """
                select 'account ' || a.id
                from student_current_account a
                where @ACCOUNTS@ and a.unallocated_credit_amount < coalesce((select sum(r.amount) from refund r
                        where r.student_current_account_id = a.id and r.status in ('REQUESTED', 'APPROVED')), 0)
                """, baselineAccountId, baselineEntryId);
    }

    public List<String> theLatestRefundStatusChangeMatchesTheCurrentStatus() {
        return violations("I-17 refund history does not end in the current refund status", """
                select 'refund ' || r.id
                from refund r join student_current_account a on a.id = r.student_current_account_id
                where @ACCOUNTS@ and coalesce((select c.to_status from refund_status_change c where c.refund_id = r.id order by c.id desc limit 1), '') <> r.status
                """, baselineAccountId, baselineEntryId);
    }

    public List<String> accountBalanceEqualsCreditsMinusDebits() {
        return violationsOf(AccountConsistencyCheck.BALANCE_DIFFERS_FROM_ENTRIES, baselineAccountId, baselineEntryId);
    }

    public List<String> runningBalancesFollowTheEntrySequence() {
        List<String> found = new ArrayList<>(violations("I-02 running balance is not the previous one moved by the amount", """
                select 'entry ' || id || ' account ' || student_current_account_id
                from (select e.id, e.student_current_account_id,
                             (case e.running_balance_side when 'OWES' then -e.running_balance_amount else e.running_balance_amount end) as running,
                             coalesce(lag(case e.running_balance_side when 'OWES' then -e.running_balance_amount else e.running_balance_amount end)
                                 over (partition by e.student_current_account_id order by e.sequence_number), 0) as previous,
                             (case e.direction when 'CREDIT' then e.amount else -e.amount end) as moved
                      from current_account_entry e where e.student_current_account_id in (select a.id from student_current_account a where @ACCOUNTS@)) steps
                where running <> previous + moved
                """, baselineAccountId, baselineEntryId));
        found.addAll(violations("I-02 sequence is not contiguous or last_entry_sequence differs from the maximum", """
                select 'account ' || a.id
                from student_current_account a
                where @ACCOUNTS@ and (a.last_entry_sequence <> coalesce((select max(e.sequence_number) from current_account_entry e where e.student_current_account_id = a.id), 0)
                   or a.last_entry_sequence <> (select count(*) from current_account_entry e where e.student_current_account_id = a.id))
                """, baselineAccountId, baselineEntryId));
        return found;
    }

    public List<String> accountBalanceIsTheUnallocatedCreditMinusTheOutstandingInvoices() {
        List<String> found = new ArrayList<>(violations("I-19 balance differs from unallocated credit minus outstanding", """
                select 'account ' || a.id
                from student_current_account a
                where @ACCOUNTS@ and a.last_entry_sequence > 0
                  and (case a.balance_side when 'OWES' then -a.balance_amount when 'IN_FAVOR' then a.balance_amount else 0 end)
                    <> a.unallocated_credit_amount - coalesce((select sum(i.outstanding_amount) from invoice i
                        where i.student_current_account_id = a.id and i.document_status = 'ISSUED'), 0)
                """, baselineAccountId, baselineEntryId));
        found.addAll(violationsOf(AccountConsistencyCheck.ISSUED_INVOICE_WITHOUT_ITS_DEBIT, baselineInvoiceId, baselineAccountId, baselineEntryId));
        return found;
    }

    private static final String NET_ALLOCATED_OF_INVOICE = """
            (select coalesce(sum(case when a.reverses_allocation_id is null then a.amount else -a.amount end), 0)
             from payment_allocation a where a.invoice_id = i.id)""";

    public List<String> invoicesAreNeverPartiallyAllocated() {
        return violations("I-03 invoice allocated in part or beyond its total", """
                select 'invoice ' || i.id
                from invoice i
                where @INVOICES@ and i.document_status = 'ISSUED' and %s not in (0, i.total - i.credited_amount)
                """.formatted(NET_ALLOCATED_OF_INVOICE), baselineInvoiceId, baselineAccountId, baselineEntryId);
    }

    public List<String> paidInvoicesAreCoveredByAllocationsOrCreditNotes() {
        return violations("I-04 paid invoice not covered by allocations or credit notes", """
                select 'invoice ' || i.id
                from invoice i
                where @INVOICES@ and i.payment_status = 'PAID' and i.total > 0
                  and %s + i.credited_amount <> i.total
                """.formatted(NET_ALLOCATED_OF_INVOICE), baselineInvoiceId, baselineAccountId, baselineEntryId);
    }

    public List<String> everyAllocationIsFundedByItsSources() {
        List<String> found = new ArrayList<>(violations("I-20 allocation whose sources do not add up to its amount", """
                select 'allocation ' || a.id
                from payment_allocation a
                where a.id > ?
                  and (select coalesce(sum(s.amount), 0) from payment_allocation_source s where s.payment_allocation_id = a.id) <> a.amount
                """, baselineAllocationId));
        found.addAll(violations("I-20 credit entry taken beyond its amount", """
                select 'entry ' || e.id
                from current_account_entry e join student_current_account a on a.id = e.student_current_account_id
                where @ACCOUNTS@
                  and e.allocatable_amount < (select coalesce(sum(case when al.reverses_allocation_id is null then s.amount else -s.amount end), 0)
                                  from payment_allocation_source s join payment_allocation al on al.id = s.payment_allocation_id
                                  where s.credit_entry_id = e.id)
                """, baselineAccountId, baselineEntryId));
        found.addAll(violations("I-20 reversed payment still funds an allocation", """
                select 'entry ' || e.id
                from current_account_entry e join student_current_account a on a.id = e.student_current_account_id
                where @ACCOUNTS@
                  and exists (select 1 from current_account_entry c where c.compensates_entry_id = e.id)
                  and (select coalesce(sum(case when s.reverses_source_id is null then s.amount else -s.amount end), 0)
                       from payment_allocation_source s where s.credit_entry_id = e.id) > 0
                """, baselineAccountId, baselineEntryId));
        return found;
    }

    public List<String> noDebtDisappearsWithoutADocument() {
        return violations("I-09 total differs from outstanding plus allocations plus credit notes", """
                select 'invoice ' || i.id
                from invoice i
                where @INVOICES@ and i.document_status = 'ISSUED'
                  and i.total <> i.outstanding_amount + %s + i.credited_amount
                """.formatted(NET_ALLOCATED_OF_INVOICE), baselineInvoiceId, baselineAccountId, baselineEntryId);
    }

    private static final String ENROLLMENT_END_OF_ORIGIN = """
            (select coalesce(pe.completion_date, ce.completion_date)::date
             from invoice_line l
             left join program_enrollment pe on pe.id = l.program_enrollment_id and pe.status in ('COMPLETED', 'WITHDRAWN')
             left join course_enrollment ce on ce.id = l.course_enrollment_id and ce.status in ('WITHDRAWN', 'APPROVED', 'FAILED', 'WAITLIST_EXPIRED')
             where l.invoice_id = c.origin_invoice_id order by l.id limit 1)""";

    private static final String ENROLLMENT_REGISTRATION_OF_ORIGIN = """
            (select coalesce(pe.withdrawal_registered_on, ce.withdrawal_registered_on)
             from invoice_line l
             left join program_enrollment pe on pe.id = l.program_enrollment_id and pe.status in ('COMPLETED', 'WITHDRAWN')
             left join course_enrollment ce on ce.id = l.course_enrollment_id and ce.status in ('WITHDRAWN', 'APPROVED', 'FAILED', 'WAITLIST_EXPIRED')
             where l.invoice_id = c.origin_invoice_id order by l.id limit 1)""";

    private static final String CORRECTION_COMPLEMENT = """
            (round(c.base_amount * c.daily_rate * (c.charged_days - (select coalesce(sum(p.corrected_days), 0) from late_fee_charge_correction p
                                                                       where p.late_fee_charge_id = c.id and p.id < k.id)), 2)
             - round(c.base_amount * c.daily_rate * (c.charged_days - (select coalesce(sum(p.corrected_days), 0) from late_fee_charge_correction p
                                                                       where p.late_fee_charge_id = c.id and p.id <= k.id)), 2))""";

    private static final String LATE_PAYMENT_VERSION_STATUSES = "('SCHEDULED', 'EFFECTIVE', 'SUPERSEDED')";

    private static final String LATE_PAYMENT_TIMELINE = """
            lp as (
              select v.topic_key, v.config_values,
                     daterange(v.effective_from, lead(v.effective_from) over (partition by v.topic_key order by v.effective_from, v.decided_at, v.id), '[)') as valid,
                     row_number() over (partition by v.topic_key order by v.effective_from, v.decided_at, v.id) = 1 as is_first
              from configuration_version v
              where v.scope = 'LATE_PAYMENT' and v.status in %s and v.topic_key in (select topic_key from charge))""".formatted(LATE_PAYMENT_VERSION_STATUSES);

    private static final String TERMS_OF_EACH_DAY = """
            left join lp m on m.topic_key = c.topic_key and g.day::date <@ m.valid
            left join lp f on f.topic_key = c.topic_key and f.is_first""";

    private static final String CHARGING_ENABLED_THAT_DAY = "coalesce(coalesce(m.config_values, f.config_values) ->> 'lateFeeChargingEnabled', 'false') = 'true'";

    private static final String NOT_FROZEN_THAT_DAY = "not (c.frozen_from is not null and g.day::date > c.frozen_from)";

    private static String chargeWithItsDayIndependentTerms(String extraColumns, String source) {
        return """
                charge as (
                  select c.*,
                         (select t.topic_key from configuration_version t where t.id = c.late_payment_version_id) as topic_key,
                         case when %1$s ->> 'freezeLateFeesOnWithdrawal' = 'true' then %2$s end as frozen_from%3$s
                  %4$s)""".formatted(termsOn(ENROLLMENT_END_OF_ORIGIN), ENROLLMENT_END_OF_ORIGIN, extraColumns, source);
    }

    private static final String PAYMENT_THRESHOLD_OF_ORIGIN = """
            (select min(case when pa.effective_date <= po.due_date + coalesce((%1$s ->> 'gracePeriodDays')::int, 0) then date '-infinity' else pa.effective_date end)
             from payment_allocation pa join invoice po on po.id = pa.invoice_id
             where pa.invoice_id = c.origin_invoice_id and pa.reverses_allocation_id is null
               and not exists (select 1 from payment_allocation pr where pr.reverses_allocation_id = pa.id))""".formatted(termsOn("po.due_date"));

    private static String termsOn(String dayExpression) {
        return """
            coalesce(
             (select v.config_values from configuration_version v
              where v.scope = 'LATE_PAYMENT' and v.topic_key = (select t.topic_key from configuration_version t where t.id = c.late_payment_version_id)
                and v.status in ('SCHEDULED', 'EFFECTIVE', 'SUPERSEDED') and v.effective_from <= %1$s
              order by v.effective_from desc, v.decided_at desc, v.id desc limit 1),
             (select v.config_values from configuration_version v
              where v.scope = 'LATE_PAYMENT' and v.topic_key = (select t.topic_key from configuration_version t where t.id = c.late_payment_version_id)
                and v.status in ('SCHEDULED', 'EFFECTIVE', 'SUPERSEDED')
              order by v.effective_from asc, v.decided_at asc, v.id asc limit 1))
            """.formatted(dayExpression);
    }

    public List<String> lateFeesRespectTheRedefinedI11() {
        List<String> found = new ArrayList<>();
        found.addAll(violations("I-11a a day of an origin is covered by two debit notes", """
                select 'origin ' || a.origin_invoice_id || ' periods ' || a.period_start || ' and ' || b.period_start
                from late_fee_charge a join late_fee_charge b on b.origin_invoice_id = a.origin_invoice_id and b.id > a.id
                where greatest(a.debit_note_id, b.debit_note_id) > ? and daterange(a.period_start, a.period_end, '[]') && daterange(b.period_start, b.period_end, '[]')
                  and a.reinstates_correction_id is null and b.reinstates_correction_id is null
                """, baselineInvoiceId));
        found.addAll(violations("I-11b a period crosses months or starts on or before the due date of its origin", """
                select 'debit note ' || c.debit_note_id
                from late_fee_charge c join invoice o on o.id = c.origin_invoice_id
                where c.debit_note_id > ? and (date_trunc('month', c.period_start) <> date_trunc('month', c.period_end) or c.period_start <= o.due_date)
                """, baselineInvoiceId));
        found.addAll(violations("I-11c charged days differ from the days with charging enabled, not frozen and not after the payment of the origin", """
                with %1$s,
                %2$s
                select 'debit note ' || c.debit_note_id
                from charge c
                where c.charged_days - (select coalesce(sum(k.corrected_days), 0) from late_fee_charge_correction k where k.late_fee_charge_id = c.id) <> (
                    select count(*) from generate_series(c.period_start, c.period_end, interval '1 day') g(day)
                    %3$s
                    where %4$s and %5$s and not (c.paid_from is not null and g.day::date > c.paid_from)
                      and not exists (select 1 from late_fee_charge_correction rk join late_fee_charge rc on rc.reinstates_correction_id = rk.id
                                      where rk.late_fee_charge_id = c.id and g.day::date between rk.first_corrected_day and rk.last_corrected_day))
                """.formatted(
                chargeWithItsDayIndependentTerms(",\n" + PAYMENT_THRESHOLD_OF_ORIGIN + " as paid_from", "from late_fee_charge c where c.debit_note_id > " + baselineInvoiceId),
                LATE_PAYMENT_TIMELINE, TERMS_OF_EACH_DAY, CHARGING_ENABLED_THAT_DAY, NOT_FROZEN_THAT_DAY)));
        found.addAll(violations("I-11c two debit notes of the same origin and month without a reversal of the payment between them", """
                select 'origin ' || a.origin_invoice_id || ' month ' || date_trunc('month', a.period_start)::date
                from late_fee_charge a join late_fee_charge b on b.origin_invoice_id = a.origin_invoice_id and b.period_start > a.period_start
                  and date_trunc('month', b.period_start) = date_trunc('month', a.period_start)
                where b.debit_note_id > ? and not exists (
                    select 1 from payment_allocation r
                    where r.invoice_id = a.origin_invoice_id and r.reverses_allocation_id is not null and r.allocated_on >= a.period_end
                      and r.allocated_on <= (select d.issue_date from invoice d where d.id = b.debit_note_id))
                """, baselineInvoiceId));
        found.addAll(violations("I-11d late fee over a debit note or over a charge that does not accrue", """
                select 'debit note ' || c.debit_note_id || ' origin ' || c.origin_invoice_id
                from late_fee_charge c join invoice o on o.id = c.origin_invoice_id
                where c.debit_note_id > ? and (o.document_type <> 'INVOICE' or o.invoice_type not in ('MONTHLY_INSTALLMENT', 'ENROLLMENT_FEE'))
                """, baselineInvoiceId));
        found.addAll(violations("I-11e late fee charged for days frozen by the version in effect that day", """
                with %1$s
                select 'debit note ' || c.debit_note_id
                from charge c
                where c.charged_days - (select coalesce(sum(k.corrected_days), 0) from late_fee_charge_correction k where k.late_fee_charge_id = c.id) > (
                    select count(*) from generate_series(c.period_start, c.period_end, interval '1 day') g(day)
                    where %2$s)
                """.formatted(
                chargeWithItsDayIndependentTerms("", "from late_fee_charge c where c.debit_note_id > ?"), NOT_FROZEN_THAT_DAY), baselineInvoiceId));
        found.addAll(violations("I-11f the amount differs from base times daily rate times charged days rounded half up", """
                select 'debit note ' || c.debit_note_id
                from late_fee_charge c join invoice d on d.id = c.debit_note_id
                where c.debit_note_id > ? and d.total <> round(c.base_amount * c.daily_rate * c.charged_days, 2)
                """, baselineInvoiceId));
        found.addAll(lateFeeCorrectionsAreConsistent());
        found.addAll(violations("I-11g late fee issued while the origin is under payment review", """
                select 'debit note ' || c.debit_note_id
                from late_fee_charge c join invoice o on o.id = c.origin_invoice_id
                where c.debit_note_id > ? and o.payment_status = 'PAYMENT_UNDER_REVIEW' and c.created_at >= (
                    select max(sc.occurred_at) from invoice_status_change sc
                    where sc.invoice_id = o.id and sc.status_kind = 'PAYMENT' and sc.to_status = 'PAYMENT_UNDER_REVIEW')
                """, baselineInvoiceId));
        return found;
    }

    public List<String> noLateFeeDebitNoteStaysLiveOverACancelledInstallment() {
        return violations("I-21 late fee debit note with balance left over a cancelled installment", """
                select 'debit note ' || c.debit_note_id
                from late_fee_charge c
                join invoice o on o.id = c.origin_invoice_id
                join invoice d on d.id = c.debit_note_id
                where c.debit_note_id > ? and o.payment_status = 'CANCELLED' and d.payment_status <> 'CANCELLED'
                  and exists (select 1 from invoice_line l where l.invoice_id = d.id
                              and (l.amount - l.discount_amount) - l.credited_amount - l.free_credited_amount > 0)
                """, baselineInvoiceId);
    }

    public List<String> lateFeeCorrectionsAreConsistent() {
        List<String> found = new ArrayList<>(lateFeeCorrectionsAfterAWithdrawalAreConsistent());
        found.addAll(lateFeeCorrectionsByPaymentDateAreConsistent());
        return found;
    }

    public List<String> lateFeeCorrectionsByPaymentDateAreConsistent() {
        return violations("I-11j late fee annulment by payment date outside its debit note, not after the payment (or inside the grace), with an allocation that is not the payment date of the origin, with another reason or recipient, or with a credit note above the complement, or below the complement while the line still had creditable balance after it", """
                select 'correction ' || k.id || ' debit note ' || k.debit_note_id
                from late_fee_charge_correction k
                join late_fee_charge c on c.id = k.late_fee_charge_id
                join invoice o on o.id = c.origin_invoice_id
                join credit_note n on n.id = k.credit_note_id
                join invoice d on d.id = k.debit_note_id
                left join payment_allocation sa on sa.id = k.settlement_allocation_id
                where k.debit_note_id > ? and k.cause <> 'BACKDATED_WITHDRAWAL' and (
                    k.debit_note_id <> c.debit_note_id
                    or k.first_corrected_day < c.period_start or k.last_corrected_day > c.period_end
                    or sa.id is null or sa.invoice_id <> c.origin_invoice_id or sa.effective_date <> k.payment_date
                    or not (k.first_corrected_day > k.payment_date
                            or (k.cause = 'PAYMENT_WITHIN_GRACE' and k.payment_date <= o.due_date + coalesce((%1$s ->> 'gracePeriodDays')::int, 0)))
                    or (k.cause = 'PAYMENT_WITHIN_GRACE' and k.payment_date > o.due_date + coalesce((%1$s ->> 'gracePeriodDays')::int, 0))
                    or n.reason <> 'LATE_FEE_ANNULMENT' or n.invoice_id <> k.debit_note_id or n.recipient_snapshot is distinct from d.student_snapshot
                    or n.total_amount > %2$s
                    or (n.total_amount < %2$s and exists (select 1 from invoice_line l
                                                          where l.invoice_id = d.id and (l.amount - l.discount_amount) - n.total_amount - (
                                                              select coalesce(sum(cl.amount), 0) from credit_note_line cl join credit_note p on p.id = cl.credit_note_id
                                                              where p.invoice_id = d.id and p.id < n.id and cl.invoice_line_number = l.line_number) > 0)))
                """.formatted(termsOn("o.due_date"), CORRECTION_COMPLEMENT), baselineInvoiceId);
    }

    public List<String> lateFeeCorrectionsAfterAWithdrawalAreConsistent() {
        return violations("I-11i late fee correction outside its debit note, within the enrollment, or issued after the withdrawal was registered, or with a credit note above the complement, or below the complement while the line still had creditable balance after it", """
                select 'correction ' || k.id || ' debit note ' || k.debit_note_id
                from late_fee_charge_correction k
                join late_fee_charge c on c.id = k.late_fee_charge_id
                join credit_note n on n.id = k.credit_note_id
                join invoice d on d.id = k.debit_note_id
                where k.debit_note_id > ? and k.cause = 'BACKDATED_WITHDRAWAL' and (
                    k.debit_note_id <> c.debit_note_id
                    or k.first_corrected_day < c.period_start or k.last_corrected_day > c.period_end
                    or %1$s is null or k.first_corrected_day <= %1$s
                    or (%2$s is not null and d.issue_date > %2$s)
                    or n.reason <> 'LATE_FEE_CORRECTION' or n.invoice_id <> k.debit_note_id or n.recipient_snapshot is distinct from d.student_snapshot
                    or n.total_amount > %3$s
                    or (n.total_amount < %3$s and exists (select 1 from invoice_line l
                                                          where l.invoice_id = d.id and (l.amount - l.discount_amount) - n.total_amount - (
                                                              select coalesce(sum(cl.amount), 0) from credit_note_line cl join credit_note p on p.id = cl.credit_note_id
                                                              where p.invoice_id = d.id and p.id < n.id and cl.invoice_line_number = l.line_number) > 0)))
                """.formatted(ENROLLMENT_END_OF_ORIGIN, ENROLLMENT_REGISTRATION_OF_ORIGIN, CORRECTION_COMPLEMENT), baselineInvoiceId);
    }

    public List<String> lateFeesCoverEveryChargeableDayAt(LocalDate today) {
        String cutoff = """
                case when o.payment_status = 'PAID' then (
                    select a.effective_date from payment_allocation a
                    where a.invoice_id = o.id and a.reverses_allocation_id is null
                      and not exists (select 1 from payment_allocation r where r.reverses_allocation_id = a.id))
                else (date_trunc('month', ?::date) - interval '1 day')::date end""";
        return violations("I-11h a chargeable day of the origin is not covered by any debit note", """
                with %1$s,
                %2$s
                select 'origin ' || c.origin_invoice_id || ' day ' || g.day::date
                from charge c
                cross join lateral generate_series(c.due_date + 1, c.cutoff, interval '1 day') g(day)
                %3$s
                where c.cutoff > c.due_date + coalesce((%4$s ->> 'gracePeriodDays')::int, 0)
                  and %5$s and %6$s
                  and not exists (select 1 from late_fee_charge x where x.origin_invoice_id = c.origin_invoice_id and g.day::date between x.period_start and x.period_end)
                """.formatted(
                chargeWithItsDayIndependentTerms("", """
                        from (select o.id as origin_invoice_id, o.due_date as due_date, %1$s as cutoff,
                                     (select min(v.id) from configuration_version v where v.scope = 'LATE_PAYMENT') as late_payment_version_id
                              from invoice o
                              where o.id > ? and o.document_type = 'INVOICE' and o.invoice_type in ('MONTHLY_INSTALLMENT', 'ENROLLMENT_FEE')
                                and o.payment_status in ('PAID', 'OVERDUE')) c""".formatted(cutoff)),
                LATE_PAYMENT_TIMELINE, TERMS_OF_EACH_DAY, termsOn("c.due_date"), CHARGING_ENABLED_THAT_DAY, NOT_FROZEN_THAT_DAY), today, baselineInvoiceId);
    }

    public void assertLateFeeInvariantsHold() {
        List<String> found = lateFeesRespectTheRedefinedI11();
        if (!found.isEmpty()) {
            throw new AssertionError("Billing invariants violated:\n" + String.join("\n", found));
        }
    }

    public void assertLateFeesAreCompleteAt(LocalDate today) {
        List<String> found = lateFeesCoverEveryChargeableDayAt(today);
        if (!found.isEmpty()) {
            throw new AssertionError("Billing invariants violated:\n" + String.join("\n", found));
        }
    }

    public List<String> atMostOneDigestPerStudentDayAndChannel() {
        return violations("I-R1 more than one digest for the same student, day and channel (one row per student and day is guaranteed by uq_collection_reminder_student_day)", """
                select 'reminder ' || r.id || ' student ' || r.student_id || ' day ' || r.business_date || ' carries ' || count(*) || ' digests in ' || m.channel
                from collection_reminder r
                cross join lateral (select 'IN_APP' as channel, e.message from jsonb_array_elements(r.in_app_messages) e(message)
                                    union all
                                    select 'EMAIL', e.message from jsonb_array_elements(r.email_messages) e(message)) m
                where r.id > ? and m.message ->> 'format' = 'DAILY_DIGEST'
                group by r.id, r.student_id, r.business_date, m.channel
                having count(*) > 1
                """, baselineReminderId);
    }

    public List<String> noEmailIsSentTwice() {
        return violations("I-R4 the same email was sent more than once", """
                select 'recipient ' || d.recipient_person_id || ' ' || d.related_entity_type || ' ' || d.related_entity_id || ' sent ' || count(*) || ' times'
                from notification_delivery d
                where d.id > ? and d.channel = 'EMAIL' and d.status in ('SENT', 'SENT_SIMULATED') and d.related_entity_id is not null
                group by d.recipient_person_id, d.related_entity_type, d.related_entity_id, d.subject, md5(d.body_text)
                having count(*) > 1
                """, baselineDeliveryId);
    }

    public List<String> reminderNoticesAreNotPlannedForSettledInvoicesOn(LocalDate day) {
        return violations("I-R2 notice planned for an invoice that was settled, cancelled or under review that day", """
                select 'reminder ' || r.id || ' day ' || r.business_date || ' ' || m.channel || ' invoice ' || i.id || ' ' || i.payment_status
                from collection_reminder r
                cross join lateral (select 'IN_APP' as channel, l.line from jsonb_path_query(r.in_app_messages, '$[*].blocks[*].lines[*] ? (@.notices.size() > 0)') l(line)
                                    union all
                                    select 'EMAIL', l.line from jsonb_path_query(r.email_messages, '$[*].blocks[*].lines[*] ? (@.notices.size() > 0)') l(line)) m
                join invoice i on i.id = (m.line ->> 'invoiceId')::bigint
                where r.id > ? and r.business_date = ?
                  and (i.payment_status in ('PAID', 'CANCELLED', 'PAYMENT_UNDER_REVIEW')
                       or exists (select 1 from bank_transfer_receipt b where b.invoice_id = i.id and b.status = 'PENDING_REVIEW')
                       or exists (select 1 from authorization_request_invoice ari join authorization_request ar on ar.id = ari.authorization_request_id
                                  where ari.invoice_id = i.id and ar.status = 'PENDING'))
                """, baselineReminderId, day);
    }

    public void assertReminderInvariantsHoldOn(LocalDate day) {
        List<String> all = new ArrayList<>(atMostOneDigestPerStudentDayAndChannel());
        all.addAll(reminderNoticesAreNotPlannedForSettledInvoicesOn(day));
        if (!all.isEmpty()) {
            throw new AssertionError("Reminder invariants violated:\n" + String.join("\n", all));
        }
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
        all.addAll(refundedAmountOfEveryPaymentEqualsItsCompletedRefunds());
        all.addAll(everyCompletedRefundHasExactlyItsRefundPaidDebit());
        all.addAll(theLatestRefundStatusChangeMatchesTheCurrentStatus());
        all.addAll(theUnallocatedCreditCoversTheAmountReservedByPendingRefunds());
        all.addAll(runningBalancesFollowTheEntrySequence());
        all.addAll(accountBalanceIsTheUnallocatedCreditMinusTheOutstandingInvoices());
        all.addAll(invoicesAreNeverPartiallyAllocated());
        all.addAll(paidInvoicesAreCoveredByAllocationsOrCreditNotes());
        all.addAll(everyAllocationIsFundedByItsSources());
        all.addAll(noDebtDisappearsWithoutADocument());
        all.addAll(lateFeesRespectTheRedefinedI11());
        all.addAll(noLateFeeDebitNoteStaysLiveOverACancelledInstallment());
        all.addAll(atMostOneDigestPerStudentDayAndChannel());
        all.addAll(noEmailIsSentTwice());
        if (!all.isEmpty()) {
            throw new AssertionError("Billing invariants violated:\n" + String.join("\n", all));
        }
    }

    private List<String> violationsOf(AccountConsistencyCheck check, Object... args) {
        String resolved = check.sql().replace("@INVOICES@", INVOICE_IS_NEW_OR_ITS_ACCOUNT_HAS_NEW_ENTRIES).replace("@ACCOUNTS@", ACCOUNT_IS_NEW_OR_HAS_NEW_ENTRIES);
        return jdbc.query(resolved, (rs, rowNum) -> check.invariant() + ": " + rs.getString(2), args);
    }

    private List<String> violations(String invariant, String sql, Object... args) {
        String resolved = sql.replace("@INVOICES@", INVOICE_IS_NEW_OR_ITS_ACCOUNT_HAS_NEW_ENTRIES).replace("@ACCOUNTS@", ACCOUNT_IS_NEW_OR_HAS_NEW_ENTRIES);
        return jdbc.queryForList(resolved, String.class, args).stream().map(row -> invariant + ": " + row).toList();
    }
}
