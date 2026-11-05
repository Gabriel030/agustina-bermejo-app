package com.red_escuela.backend.billing.run.domain;

import java.util.List;

public enum AccountConsistencyCheck {
    BALANCE_DIFFERS_FROM_ENTRIES("INCONSISTENT_ACCOUNT", "I-01 account balance differs from credits minus debits", false, """
            select a.id, 'account ' || a.id || ' difference ' || abs(
                    (case a.balance_side when 'OWES' then -a.balance_amount when 'IN_FAVOR' then a.balance_amount else 0 end)
                    - coalesce((select sum(case e.direction when 'CREDIT' then e.amount else -e.amount end)
                                from current_account_entry e where e.student_current_account_id = a.id), 0))
            from student_current_account a
            where @ACCOUNTS@ and (case a.balance_side when 'OWES' then -a.balance_amount when 'IN_FAVOR' then a.balance_amount else 0 end)
                <> coalesce((select sum(case e.direction when 'CREDIT' then e.amount else -e.amount end)
                             from current_account_entry e where e.student_current_account_id = a.id), 0)
            """),
    ISSUED_INVOICE_WITHOUT_ITS_DEBIT("ISSUED_INVOICE_WITHOUT_DEBIT", "I-19 issued invoice without exactly one debit (or a zero total invoice with one)", false, """
            select i.student_current_account_id, 'invoice ' || i.id
            from invoice i
            where @INVOICES@ and i.document_status = 'ISSUED'
              and exists (select 1 from student_current_account a where a.id = i.student_current_account_id and a.last_entry_sequence > 0)
              and (select count(*) from current_account_entry e where e.invoice_id = i.id) <> (case when i.total > 0 then 1 else 0 end)
            """),
    APPROVED_PAYMENT_WITHOUT_ITS_CREDIT("APPROVED_PAYMENT_WITHOUT_CREDIT", "approved payment without its credit entry", false, """
            select p.student_current_account_id, 'payment ' || p.id
            from payment p join student_current_account a on a.id = p.student_current_account_id
            where @ACCOUNTS@ and p.status = 'APPROVED'
              and not exists (select 1 from current_account_entry e where e.payment_id = p.id and e.entry_type = 'PAYMENT_RECEIVED')
            """),
    CHARGE_KEY_HELD_BY_MORE_THAN_ONE_LIVE_LINE("DUPLICATE_LIVE_CHARGE_KEY", "I-05 charge key held by more than one live invoice line", false, """
            select distinct i.student_current_account_id, 'charge key ' || l.charge_key
            from invoice_line l join invoice i on i.id = l.invoice_id
            where l.charge_key_released_at is null and i.document_status <> 'DISCARDED'
              and (i.payment_status is null or i.payment_status <> 'CANCELLED')
              and l.charge_key in (
                select l2.charge_key from invoice_line l2 join invoice i2 on i2.id = l2.invoice_id
                where l2.charge_key_released_at is null and i2.document_status <> 'DISCARDED'
                  and (i2.payment_status is null or i2.payment_status <> 'CANCELLED')
                group by l2.charge_key having count(*) > 1)
            """),
    PAYMENT_STATUS_DIFFERS_FROM_AMOUNTS("PAYMENT_STATUS_MISMATCH", "I-08 payment status inconsistent with the amounts", false, """
            select i.student_current_account_id, 'invoice ' || i.id || ' ' || i.payment_status
            from invoice i
            where @INVOICES@ and ((i.payment_status in ('PENDING', 'PAYMENT_UNDER_REVIEW', 'OVERDUE') and not (i.paid_amount = 0 and i.outstanding_amount > 0))
               or (i.payment_status = 'PAID' and i.outstanding_amount <> 0)
               or (i.payment_status = 'CANCELLED' and not (i.paid_amount = 0 and i.outstanding_amount = 0 and i.credited_amount = i.total))
               or (i.payment_status is null and (i.paid_amount <> 0 or i.credited_amount <> 0)))
            """),
    OVERDUE_STATUS_BEFORE_THE_DUE_DATE("PAYMENT_STATUS_MISMATCH", "invoice marked overdue before its due date", true, """
            select i.student_current_account_id, 'invoice ' || i.id || ' due ' || i.due_date
            from invoice i
            where @INVOICES@ and i.payment_status = 'OVERDUE' and i.due_date >= ?
            """);

    private final String findingCode;
    private final String invariant;
    private final boolean takesBusinessDate;
    private final String sql;

    AccountConsistencyCheck(String findingCode, String invariant, boolean takesBusinessDate, String sql) {
        this.findingCode = findingCode;
        this.invariant = invariant;
        this.takesBusinessDate = takesBusinessDate;
        this.sql = sql;
    }

    public String findingCode() {
        return findingCode;
    }

    public String invariant() {
        return invariant;
    }

    public boolean takesBusinessDate() {
        return takesBusinessDate;
    }

    public String sql() {
        return sql;
    }

    public static List<AccountConsistencyCheck> inPriorityOrder() {
        return List.of(values());
    }
}
