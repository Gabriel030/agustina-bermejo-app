package com.red_escuela.backend.billing.invoice.repository;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Tag("D-171")
@Tag("D-136")
@Tag("D-165")
@Tag("D-057")
@Tag("D-097")
@Tag("D-075")
@Tag("D-060")
@Tag("D-051")
class InvoiceSchemaConstraintIT extends AbstractIntegrationTest {

    private static final String NEGATIVE = "violates check constraint";

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private long newPersistedStudent() {
        Long addressId = jdbc.queryForObject("insert into address default values returning id", Long.class);
        String username = "schema" + System.nanoTime();
        Long personId = jdbc.queryForObject("insert into person (username, email, address_id, status) values (?, ?, ?, 'ACTIVE') returning id",
                Long.class, username, username + "@example.com", addressId);
        Long institutionId = jdbc.queryForObject("insert into institution (name) values (?) returning id", Long.class, "Inst " + System.nanoTime());
        return jdbc.queryForObject("insert into student (person_id, institution_id, onboarding_status) values (?, ?, 'CONFIRMADO') returning id",
                Long.class, personId, institutionId);
    }

    private long insertAccount(String currency) {
        return jdbc.queryForObject("""
                insert into student_current_account (student_id, currency, balance_amount, balance_amount_currency, balance_side,
                    unallocated_credit_amount, unallocated_credit_amount_currency, opened_at)
                values (?, ?, 0, ?, 'ZERO', 0, ?, now()) returning id
                """, Long.class, newPersistedStudent(), currency, currency, currency);
    }

    private Map<String, Object> issuedInvoice(long accountId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("student_current_account_id", accountId);
        row.put("currency", "ARS");
        row.put("document_type", "INVOICE");
        row.put("invoice_type", "MONTHLY_INSTALLMENT");
        row.put("issuing_reason", "MONTHLY_RUN");
        row.put("document_status", "ISSUED");
        row.put("payment_status", "PENDING");
        row.put("invoice_number", "F-" + String.format("%08d", System.nanoTime() % 100_000_000L));
        row.put("issue_date", LocalDate.of(2027, 3, 3));
        row.put("due_date", LocalDate.of(2027, 3, 13));
        row.put("student_snapshot", "{\"fullName\":\"Ana\"}");
        for (String column : new String[]{"lines_total", "total", "outstanding_amount"}) {
            row.put(column, 100);
        }
        for (String column : new String[]{"discounts_total", "paid_amount", "credited_amount"}) {
            row.put(column, 0);
        }
        for (String column : new String[]{"lines_total", "discounts_total", "total", "paid_amount", "credited_amount", "outstanding_amount"}) {
            row.put(column + "_currency", "ARS");
        }
        row.put("created_at", LocalDateTime.now());
        row.put("updated_at", LocalDateTime.now());
        return row;
    }

    private Map<String, Object> draftInvoice(long accountId) {
        Map<String, Object> row = issuedInvoice(accountId);
        row.put("document_status", "DRAFT");
        for (String column : new String[]{"payment_status", "invoice_number", "issue_date", "due_date", "student_snapshot"}) {
            row.put(column, null);
        }
        return row;
    }

    private long insert(String table, Map<String, Object> row) {
        String columns = String.join(", ", row.keySet());
        String placeholders = String.join(", ", row.keySet().stream().map(c -> c.equals("student_snapshot") ? "cast(? as jsonb)" : "?").toList());
        return jdbc.queryForObject("insert into " + table + " (" + columns + ") values (" + placeholders + ") returning id", Long.class, row.values().toArray());
    }

    private void expectViolation(String constraint, Runnable statements) {
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            try {
                statements.run();
            } finally {
                status.setRollbackOnly();
            }
        })).isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining(constraint);
    }

    private Map<String, Object> with(Map<String, Object> row, Object... pairs) {
        for (int i = 0; i < pairs.length; i += 2) {
            row.put((String) pairs[i], pairs[i + 1]);
        }
        return row;
    }

    @Test
    void theValidBaseRowsAreAccepted() {
        long account = insertAccount("ARS");

        assertThatCode(() -> transactionTemplate.executeWithoutResult(status -> {
            insert("invoice", issuedInvoice(account));
            insert("invoice", draftInvoice(account));
            status.setRollbackOnly();
        })).doesNotThrowAnyException();
    }

    @Test
    void everyInvoiceCheckRejectsItsViolatingRow() {
        long account = insertAccount("ARS");
        expectViolation("ck_invoice_amount_currencies", () -> insert("invoice", with(issuedInvoice(account), "lines_total_currency", "USD")));
        expectViolation("ck_invoice_total_formula", () -> insert("invoice", with(issuedInvoice(account), "total", 101, "outstanding_amount", 101)));
        expectViolation("ck_invoice_outstanding_formula", () -> insert("invoice", with(issuedInvoice(account), "outstanding_amount", 99)));
        expectViolation("ck_invoice_no_partial_payment", () -> insert("invoice", with(issuedInvoice(account), "paid_amount", 40, "outstanding_amount", 60, "payment_status", "PAID")));
        expectViolation("ck_invoice_payment_status_only_when_issued", () -> insert("invoice", with(draftInvoice(account), "payment_status", "PENDING")));
        expectViolation("ck_invoice_payment_status_only_when_issued", () -> insert("invoice", with(issuedInvoice(account), "payment_status", null)));
        expectViolation("ck_invoice_issued_fields_present", () -> insert("invoice", with(issuedInvoice(account), "invoice_number", null)));
        expectViolation("ck_invoice_issued_fields_absent", () -> insert("invoice", with(draftInvoice(account), "issue_date", LocalDate.of(2027, 3, 3))));
        expectViolation("ck_invoice_due_date_not_before_issue_date", () -> insert("invoice", with(issuedInvoice(account), "due_date", LocalDate.of(2027, 3, 2))));
        expectViolation("ck_invoice_payment_deadline_only_annual", () -> insert("invoice", with(draftInvoice(account), "payment_deadline", LocalDateTime.now())));
        expectViolation("ck_invoice_awaiting_payment_has_deadline", () -> insert("invoice", with(draftInvoice(account), "invoice_type", "ANNUAL_UPFRONT", "document_status", "AWAITING_PAYMENT")));
        expectViolation("ck_invoice_no_money_before_issue", () -> insert("invoice", with(draftInvoice(account), "credited_amount", 10, "outstanding_amount", 90)));
        expectViolation("ck_invoice_open_status_amounts", () -> insert("invoice", with(issuedInvoice(account), "total", 0, "lines_total", 0, "outstanding_amount", 0)));
        expectViolation("ck_invoice_paid_status_amounts", () -> insert("invoice", with(issuedInvoice(account), "payment_status", "PAID")));
        expectViolation("ck_invoice_cancelled_status_amounts", () -> insert("invoice", with(issuedInvoice(account), "payment_status", "CANCELLED", "credited_amount", 0, "outstanding_amount", 0, "total", 100)));
        expectViolation("ck_invoice_zero_total_is_paid", () -> insert("invoice", with(issuedInvoice(account), "payment_status", "CANCELLED", "total", 0, "lines_total", 0, "outstanding_amount", 0)));
        expectViolation("ck_invoice_debit_note_is_late_fee", () -> insert("invoice", with(draftInvoice(account), "invoice_type", "LATE_FEE")));
        expectViolation("ck_invoice_debit_note_has_origin", () -> insert("invoice", with(draftInvoice(account), "document_type", "DEBIT_NOTE", "invoice_type", "LATE_FEE")));
        expectViolation("ck_invoice_discarded_reason", () -> insert("invoice", with(draftInvoice(account), "document_status", "DISCARDED")));
        expectViolation("ck_invoice_discarded_reason", () -> insert("invoice", with(draftInvoice(account), "discarded_reason", "x")));
        expectViolation("ck_invoice_origin_not_itself", () -> insert("invoice", with(draftInvoice(account), "id", 987654321L, "document_type", "DEBIT_NOTE", "invoice_type", "LATE_FEE", "origin_invoice_id", 987654321L)));
        expectViolation("uk_invoice_number", () -> {
            Map<String, Object> first = issuedInvoice(account);
            insert("invoice", first);
            insert("invoice", with(issuedInvoice(account), "invoice_number", first.get("invoice_number")));
        });
        expectViolation("invoice_document_type_check", () -> insert("invoice", with(draftInvoice(account), "document_type", "RECEIPT")));
        expectViolation("invoice_payment_status_check", () -> insert("invoice", with(issuedInvoice(account), "payment_status", "WAIVED")));
        expectViolation("invoice_payment_status_check", () -> insert("invoice", with(issuedInvoice(account), "payment_status", "PARTIALLY_PAID")));
    }

    @Test
    void theCompositeForeignKeysForceTheSameAccountAndCurrency() {
        long arsAccount = insertAccount("ARS");
        long otherAccount = insertAccount("ARS");
        expectViolation("fk_invoice_student_current_account", () -> insert("invoice", with(draftInvoice(arsAccount), "currency", "USD",
                "lines_total_currency", "USD", "discounts_total_currency", "USD", "total_currency", "USD", "paid_amount_currency", "USD",
                "credited_amount_currency", "USD", "outstanding_amount_currency", "USD")));
        expectViolation("fk_invoice_origin", () -> {
            long origin = insert("invoice", draftInvoice(otherAccount));
            insert("invoice", with(draftInvoice(arsAccount), "document_type", "DEBIT_NOTE", "invoice_type", "LATE_FEE", "origin_invoice_id", origin));
        });
        expectViolation("fk_invoice_line_invoice", () -> {
            long invoice = insert("invoice", draftInvoice(arsAccount));
            insert("invoice_line", with(line(invoice), "currency", "USD", "amount_currency", "USD", "discount_amount_currency", "USD", "credited_amount_currency", "USD"));
        });
    }

    private Map<String, Object> line(long invoiceId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("invoice_id", invoiceId);
        row.put("currency", "ARS");
        row.put("line_number", 1);
        row.put("fee_type", "ADMINISTRATIVE_FEE");
        row.put("concept", "Tasa");
        row.put("period", LocalDate.of(2027, 3, 1));
        row.put("charge_key", "K" + System.nanoTime());
        row.put("amount", 100);
        row.put("amount_currency", "ARS");
        row.put("discount_amount", 0);
        row.put("discount_amount_currency", "ARS");
        row.put("credited_amount", 0);
        row.put("credited_amount_currency", "ARS");
        row.put("free_credited_amount", 0);
        row.put("free_credited_amount_currency", "ARS");
        return row;
    }

    @Test
    void everyLineAndDiscountConstraintRejectsItsViolatingRow() {
        long account = insertAccount("ARS");
        expectViolation("ck_invoice_line_currencies", () -> insert("invoice_line", with(line(insert("invoice", draftInvoice(account))), "amount_currency", "USD")));
        expectViolation(NEGATIVE, () -> insert("invoice_line", with(line(insert("invoice", draftInvoice(account))), "discount_amount", 101)));
        expectViolation("ck_invoice_line_credited_within_net", () -> insert("invoice_line", with(line(insert("invoice", draftInvoice(account))), "credited_amount", 101)));
        expectViolation("ck_invoice_line_single_enrollment", () -> insert("invoice_line", with(line(insert("invoice", draftInvoice(account))), "program_enrollment_id", 1, "course_enrollment_id", 1)));
        expectViolation("ck_invoice_line_priced_fees_have_price_revision", () -> insert("invoice_line", with(line(insert("invoice", draftInvoice(account))), "fee_type", "MONTHLY_INSTALLMENT")));
        expectViolation("uk_invoice_line_number", () -> {
            long invoice = insert("invoice", draftInvoice(account));
            insert("invoice_line", line(invoice));
            insert("invoice_line", line(invoice));
        });
        expectViolation("uk_invoice_line_charge_key", () -> {
            insert("invoice_line", with(line(insert("invoice", draftInvoice(account))), "charge_key", "DUP"));
            insert("invoice_line", with(line(insert("invoice", draftInvoice(account))), "charge_key", "DUP"));
        });
        assertThatCode(() -> transactionTemplate.executeWithoutResult(status -> {
            insert("invoice_line", with(line(insert("invoice", draftInvoice(account))), "charge_key", "REL", "charge_key_released_at", LocalDateTime.now()));
            insert("invoice_line", with(line(insert("invoice", draftInvoice(account))), "charge_key", "REL"));
            status.setRollbackOnly();
        })).doesNotThrowAnyException();
        expectViolation("invoice_line_amount_check", () -> insert("invoice_line", with(line(insert("invoice", draftInvoice(account))), "amount", 0)));
        expectViolation("invoice_line_line_number_check", () -> insert("invoice_line", with(line(insert("invoice", draftInvoice(account))), "line_number", 0)));
        expectViolation("invoice_line_charge_key_check", () -> insert("invoice_line", with(line(insert("invoice", draftInvoice(account))), "charge_key", "")));
        expectViolation("ck_invoice_discount_positive_within_base", () -> insert("invoice_discount", discountRow(insert("invoice_line", line(insert("invoice", draftInvoice(account)))), 10, 11)));
        expectViolation("ck_invoice_discount_positive_within_base", () -> insert("invoice_discount", discountRow(insert("invoice_line", line(insert("invoice", draftInvoice(account)))), 10, 0)));
        expectViolation("ck_invoice_discount_currencies", () -> insert("invoice_discount", with(discountRow(insert("invoice_line", line(insert("invoice", draftInvoice(account)))), 10, 5), "base_amount_currency", "USD")));
        expectViolation("ck_invoice_discount_installments", () -> insert("invoice_discount", with(discountRow(insert("invoice_line", line(insert("invoice", draftInvoice(account)))), 10, 5), "installment_number", 4, "installments_total", 3)));
        expectViolation("ck_invoice_discount_installments", () -> insert("invoice_discount", with(discountRow(insert("invoice_line", line(insert("invoice", draftInvoice(account)))), 10, 5), "installments_total", 3)));
        expectViolation("fk_invoice_discount_line", () -> insert("invoice_discount", with(discountRow(insert("invoice_line", line(insert("invoice", draftInvoice(account)))), 10, 5),
                "currency", "USD", "base_amount_currency", "USD", "discount_amount_currency", "USD")));
        expectViolation("ck_invoice_line_free_credited_within_net", () -> insert("invoice_line", with(line(insert("invoice", draftInvoice(account))), "free_credited_amount", 101)));
        expectViolation("uk_invoice_discount_line", () -> {
            long line = insert("invoice_line", line(insert("invoice", draftInvoice(account))));
            insert("invoice_discount", discountRow(line, 10, 5));
            insert("invoice_discount", discountRow(line, 10, 5));
        });
    }

    private Map<String, Object> discountRow(long lineId, int base, int discount) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("invoice_line_id", lineId);
        row.put("discount_type", "MANUAL");
        row.put("concept", "Manual");
        row.put("currency", "ARS");
        row.put("base_amount", base);
        row.put("base_amount_currency", "ARS");
        row.put("discount_amount", discount);
        row.put("discount_amount_currency", "ARS");
        return row;
    }

    @ParameterizedTest
    @ValueSource(strings = {"lines_total", "discounts_total", "total", "paid_amount", "credited_amount", "outstanding_amount"})
    void aNegativeAmountInAnyInvoiceColumnIsRejected(String column) {
        long account = insertAccount("ARS");

        expectViolation(NEGATIVE, () -> insert("invoice", with(draftInvoice(account), column, -0.01)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"amount", "discount_amount", "credited_amount", "free_credited_amount"})
    void aNegativeAmountInAnyLineColumnIsRejected(String column) {
        long account = insertAccount("ARS");

        expectViolation(NEGATIVE, () -> insert("invoice_line", with(line(insert("invoice", draftInvoice(account))), column, -0.01)));
    }

    @Test
    void theAccountAndEntryTablesRejectTheirInvalidRows() {
        long student = newPersistedStudent();
        jdbc.update("""
                insert into student_current_account (student_id, currency, balance_amount, balance_amount_currency, balance_side,
                    unallocated_credit_amount, unallocated_credit_amount_currency, opened_at) values (?, 'ARS', 0, 'ARS', 'ZERO', 0, 'ARS', now())
                """, student);
        expectViolation("uk_student_current_account_student_currency", () -> jdbc.update("""
                insert into student_current_account (student_id, currency, balance_amount, balance_amount_currency, balance_side,
                    unallocated_credit_amount, unallocated_credit_amount_currency, opened_at) values (?, 'ARS', 0, 'ARS', 'ZERO', 0, 'ARS', now())
                """, student));
        expectViolation("ck_student_current_account_zero_side", () -> jdbc.update("""
                insert into student_current_account (student_id, currency, balance_amount, balance_amount_currency, balance_side,
                    unallocated_credit_amount, unallocated_credit_amount_currency, opened_at) values (?, 'USD', 5, 'USD', 'ZERO', 0, 'USD', now())
                """, student));
        expectViolation("ck_student_current_account_balance_currency", () -> jdbc.update("""
                insert into student_current_account (student_id, currency, balance_amount, balance_amount_currency, balance_side,
                    unallocated_credit_amount, unallocated_credit_amount_currency, opened_at) values (?, 'USD', 0, 'ARS', 'ZERO', 0, 'USD', now())
                """, student));
        expectViolation("ck_student_current_account_unallocated_currency", () -> jdbc.update("""
                insert into student_current_account (student_id, currency, balance_amount, balance_amount_currency, balance_side,
                    unallocated_credit_amount, unallocated_credit_amount_currency, opened_at) values (?, 'USD', 0, 'USD', 'ZERO', 0, 'ARS', now())
                """, student));
        expectViolation("student_current_account_unallocated_credit_amount_check", () -> jdbc.update("""
                insert into student_current_account (student_id, currency, balance_amount, balance_amount_currency, balance_side,
                    unallocated_credit_amount, unallocated_credit_amount_currency, opened_at) values (?, 'USD', 0, 'USD', 'ZERO', -0.01, 'USD', now())
                """, student));

        long account = insertAccount("ARS");
        expectViolation("ck_current_account_entry_direction_by_type", () -> entry(account, 1, "CREDIT", "INVOICE_ISSUED", 10, "OWES", 10, "invoice_id", insert("invoice", issuedInvoice(account))));
        expectViolation("ck_current_account_entry_source_by_type", () -> entry(account, 1, "DEBIT", "INVOICE_ISSUED", 10, "OWES", 10));
        expectViolation("ck_current_account_entry_zero_side", () -> entry(account, 1, "CREDIT", "PAYMENT_RECEIVED", 10, "ZERO", 10, "payment_id", 1));
        expectViolation("ck_current_account_entry_currencies", () -> {
            Map<String, Object> row = entryRow(account, 1, "CREDIT", "PAYMENT_RECEIVED", 10, "IN_FAVOR", 10, "payment_id", 1);
            row.put("amount_currency", "USD");
            insert("current_account_entry", row);
        });
        expectViolation("uk_current_account_entry_sequence", () -> {
            entry(account, 1, "CREDIT", "PAYMENT_RECEIVED", 10, "IN_FAVOR", 10, "payment_id", 1);
            entry(account, 1, "CREDIT", "PAYMENT_RECEIVED", 10, "IN_FAVOR", 20, "payment_id", 2);
        });
        expectViolation("uk_current_account_entry_payment_credit", () -> {
            entry(account, 1, "CREDIT", "PAYMENT_RECEIVED", 10, "IN_FAVOR", 10, "payment_id", 1);
            entry(account, 2, "CREDIT", "PAYMENT_RECEIVED", 10, "IN_FAVOR", 20, "payment_id", 1);
        });
        expectViolation("uk_current_account_entry_invoice_debit", () -> {
            long invoice = insert("invoice", issuedInvoice(account));
            entry(account, 1, "DEBIT", "INVOICE_ISSUED", 10, "OWES", 10, "invoice_id", invoice);
            entry(account, 2, "DEBIT", "INVOICE_ISSUED", 10, "OWES", 20, "invoice_id", invoice);
        });
        expectViolation("fk_current_account_entry_invoice", () -> entry(account, 1, "DEBIT", "INVOICE_ISSUED", 10, "OWES", 10,
                "invoice_id", insert("invoice", issuedInvoice(insertAccount("ARS")))));
        expectViolation("current_account_entry_amount_check", () -> entry(account, 1, "CREDIT", "PAYMENT_RECEIVED", 0, "IN_FAVOR", 10, "payment_id", 1));
        expectViolation("uk_current_account_entry_compensates", () -> {
            entry(account, 1, "CREDIT", "PAYMENT_RECEIVED", 10, "IN_FAVOR", 10, "payment_id", 1);
            long original = jdbc.queryForObject("select id from current_account_entry where student_current_account_id = ? and sequence_number = 1", Long.class, account);
            entry(account, 2, "DEBIT", "PAYMENT_REVERSED", 10, "ZERO", 0, "payment_id", 1, "compensates_entry_id", original);
            entry(account, 3, "DEBIT", "PAYMENT_REVERSED", 10, "OWES", 10, "payment_id", 1, "compensates_entry_id", original);
        });
    }

    private Map<String, Object> entryRow(long account, long sequence, String direction, String type, int amount, String side, int running, Object... source) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("student_current_account_id", account);
        row.put("currency", "ARS");
        row.put("sequence_number", sequence);
        row.put("direction", direction);
        row.put("entry_type", type);
        row.put("amount", amount);
        row.put("amount_currency", "ARS");
        row.put("running_balance_amount", running);
        row.put("running_balance_amount_currency", "ARS");
        row.put("running_balance_side", side);
        row.put("effective_date", LocalDate.of(2027, 3, 3));
        row.put("recorded_at", LocalDateTime.now());
        row.put("concept", "Movimiento");
        for (int i = 0; i < source.length; i += 2) {
            row.put((String) source[i], source[i + 1]);
        }
        return row;
    }

    private void entry(long account, long sequence, String direction, String type, int amount, String side, int running, Object... source) {
        insert("current_account_entry", entryRow(account, sequence, direction, type, amount, side, running, source));
    }

    @Test
    void theEntryTableIsAppendOnly() {
        long account = insertAccount("ARS");
        Long id = transactionTemplate.execute(status -> insert("current_account_entry", entryRow(account, 1, "CREDIT", "PAYMENT_RECEIVED", 10, "IN_FAVOR", 10, "payment_id", System.nanoTime())));

        assertThatThrownBy(() -> jdbc.update("update current_account_entry set concept = 'x' where id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("delete from current_account_entry where id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("append-only");
        assertThat(jdbc.queryForObject("select count(*) from current_account_entry where id = ?", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void theStatusChangeChecksRejectInvalidRows() {
        long account = insertAccount("ARS");
        expectViolation("ck_invoice_status_change_one_actor", () -> statusChange(insert("invoice", draftInvoice(account)), "DOCUMENT", "DRAFT", null));
        expectViolation("ck_invoice_status_change_one_actor", () -> statusChange(insert("invoice", draftInvoice(account)), "DOCUMENT", "DRAFT", "JOB", 1L));
        expectViolation("ck_invoice_status_change_values", () -> statusChange(insert("invoice", draftInvoice(account)), "DOCUMENT", "PAID", "JOB"));
        expectViolation("ck_invoice_status_change_values", () -> statusChange(insert("invoice", draftInvoice(account)), "PAYMENT", "WAIVED", "JOB"));
        expectViolation("ck_invoice_status_change_values", () -> statusChange(insert("invoice", draftInvoice(account)), "PAYMENT", "PARTIALLY_PAID", "JOB"));
    }

    private void statusChange(long invoice, String kind, String toStatus, String process, Object... personId) {
        jdbc.update("""
                insert into invoice_status_change (invoice_id, status_kind, to_status, event, actor_person_id, actor_process, occurred_at)
                values (?, ?, ?, 'E', ?, ?, now())
                """, invoice, kind, toStatus, personId.length > 0 ? personId[0] : null, process);
    }

    @Test
    void anIssuedInvoiceLineAndDiscountAreImmutableExceptForCreditsAndKeyRelease() {
        long account = insertAccount("ARS");
        long invoice = insert("invoice", draftInvoice(account));
        long line = insert("invoice_line", line(invoice));
        long discount = insert("invoice_discount", discountRow(line, 10, 5));
        jdbc.update("update invoice set document_status = 'ISSUED', payment_status = 'PENDING', invoice_number = 'F-77777777', issue_date = date '2027-03-03', "
                + "due_date = date '2027-03-13', student_snapshot = cast('{\"fullName\":\"Ana\"}' as jsonb) where id = ?", invoice);

        assertThatThrownBy(() -> jdbc.update("update invoice_line set amount = 50 where id = ?", line)).isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("immutable");
        assertThatThrownBy(() -> jdbc.update("update invoice_line set discount_amount = 1 where id = ?", line)).isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("immutable");
        assertThatThrownBy(() -> jdbc.update("delete from invoice_line where id = ?", line)).isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("immutable");
        assertThatThrownBy(() -> jdbc.update("update invoice_discount set discount_amount = 6 where id = ?", discount)).isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("immutable");
        assertThatThrownBy(() -> jdbc.update("delete from invoice_discount where id = ?", discount)).isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("immutable");
        assertThat(jdbc.update("update invoice_line set credited_amount = 50, free_credited_amount = 10 where id = ?", line)).isEqualTo(1);
        assertThat(jdbc.update("update invoice_line set charge_key_released_at = now() where id = ?", line)).isEqualTo(1);
    }

    @Test
    void aDraftLineStillAcceptsChanges() {
        long account = insertAccount("ARS");
        long line = insert("invoice_line", line(insert("invoice", draftInvoice(account))));

        assertThat(jdbc.update("update invoice_line set concept = 'otro' where id = ?", line)).isEqualTo(1);
        assertThat(jdbc.update("delete from invoice_line where id = ?", line)).isEqualTo(1);
    }
}
