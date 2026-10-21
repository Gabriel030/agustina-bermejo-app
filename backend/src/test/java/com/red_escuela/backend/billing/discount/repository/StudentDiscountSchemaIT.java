package com.red_escuela.backend.billing.discount.repository;

import com.red_escuela.backend.billing.invoice.operation.IssuingScenarioFactory;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.shared.domain.Currency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Tag("D-282")
@Tag("D-284")
@Tag("D-384")
@Tag("D-036")
class StudentDiscountSchemaIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private IssuingScenarioFactory factory;

    private IssuingScenarioFactory.Offering offering;
    private IssuingScenarioFactory.Learner learner;
    private Long scholarshipDefinitionId;

    @BeforeEach
    void setUp() {
        offering = factory.newProgram(Currency.ARS, 80000, 50000);
        learner = factory.newLearner(offering, null);
        scholarshipDefinitionId = jdbc.queryForObject("select min(id) from discount_definition where discount_type = 'SCHOLARSHIP'", Long.class);
    }

    private Map<String, Object> validRow() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("program_enrollment_id", learner.programEnrollmentId());
        row.put("student_id", learner.studentId());
        row.put("currency", "ARS");
        row.put("discount_definition_id", scholarshipDefinitionId);
        row.put("discount_type", "SCHOLARSHIP");
        row.put("applicable_invoice_type", "MONTHLY_INSTALLMENT");
        row.put("origin", "DIRECT_GRANT");
        row.put("calculation", "{\"type\":\"PROPORTIONAL\",\"ratePercent\":50}");
        row.put("priority", 1);
        row.put("from_period", java.time.LocalDate.of(2027, 4, 1));
        row.put("to_period", java.time.LocalDate.of(2027, 8, 1));
        row.put("status", "ACTIVE");
        row.put("granted_at", java.time.OffsetDateTime.parse("2027-03-10T10:00:00Z"));
        row.put("granted_by_id", offering.directorId());
        row.put("reason", "motivo");
        return row;
    }

    private Map<String, Object> with(Map<String, Object> row, Object... pairs) {
        for (int i = 0; i < pairs.length; i += 2) {
            row.put((String) pairs[i], pairs[i + 1]);
        }
        return row;
    }

    private long insert(Map<String, Object> row) {
        String placeholders = String.join(", ", row.keySet().stream().map(column -> column.equals("calculation") ? "cast(? as jsonb)" : "?").toList());
        return jdbc.queryForObject("insert into student_discount (" + String.join(", ", row.keySet()) + ") values (" + placeholders + ") returning id", Long.class,
                row.values().toArray());
    }

    private void expectViolation(String constraint, Map<String, Object> row) {
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            try {
                insert(row);
            } finally {
                status.setRollbackOnly();
            }
        })).isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining(constraint);
    }

    private long insertIssuedDiscount(String number) {
        Long accountId = jdbc.queryForObject("""
                insert into student_current_account (student_id, currency, balance_amount, balance_amount_currency, balance_side,
                    unallocated_credit_amount, unallocated_credit_amount_currency, opened_at)
                values (?, 'ARS', 0, 'ARS', 'ZERO', 0, 'ARS', now()) returning id
                """, Long.class, factory.newLearner(offering, null).studentId());
        Long invoiceId = jdbc.queryForObject("""
                insert into invoice (student_current_account_id, currency, document_type, invoice_type, issuing_reason, document_status,
                    lines_total, lines_total_currency, discounts_total, discounts_total_currency, total, total_currency, paid_amount, paid_amount_currency,
                    credited_amount, credited_amount_currency, outstanding_amount, outstanding_amount_currency, created_at, updated_at)
                values (?, 'ARS', 'INVOICE', 'MONTHLY_INSTALLMENT', 'MONTHLY_RUN', 'DRAFT', 100, 'ARS', 0, 'ARS', 100, 'ARS', 0, 'ARS', 0, 'ARS', 100, 'ARS', now(), now())
                returning id
                """, Long.class, accountId);
        Long lineId = jdbc.queryForObject("""
                insert into invoice_line (invoice_id, currency, line_number, fee_type, concept, period, charge_key, amount, amount_currency, discount_amount,
                    discount_amount_currency, credited_amount, credited_amount_currency, free_credited_amount, free_credited_amount_currency)
                values (?, 'ARS', 1, 'ADMINISTRATIVE_FEE', 'Tasa', date '2027-03-01', ?, 100, 'ARS', 0, 'ARS', 0, 'ARS', 0, 'ARS') returning id
                """, Long.class, invoiceId, "K" + number + System.nanoTime());
        Long discountId = jdbc.queryForObject("""
                insert into invoice_discount (invoice_line_id, discount_type, concept, currency, base_amount, base_amount_currency, discount_amount, discount_amount_currency)
                values (?, 'INDIVIDUAL', 'Individual', 'ARS', 100, 'ARS', 10, 'ARS') returning id
                """, Long.class, lineId);
        jdbc.update("update invoice set document_status = 'ISSUED', payment_status = 'PENDING', invoice_number = ?, issue_date = date '2027-03-03', "
                + "due_date = date '2027-03-13', student_snapshot = cast('{\"fullName\":\"Ana\"}' as jsonb) where id = ?", number, invoiceId);
        return discountId;
    }

    @Test
    void aValidScholarshipRowAndAnIndividualRowAreAccepted() {
        transactionTemplate.executeWithoutResult(status -> {
            assertThat(insert(validRow())).isPositive();
            assertThat(insert(with(validRow(), "discount_type", "INDIVIDUAL", "discount_definition_id", null, "applicable_invoice_type", "ENROLLMENT_FEE"))).isPositive();
            status.setRollbackOnly();
        });
    }

    @Test
    void everyStudentDiscountConstraintRejectsItsViolatingRow() {
        expectViolation("ck_student_discount_single_enrollment", with(validRow(), "course_enrollment_id", learner.programEnrollmentId()));
        expectViolation("ck_student_discount_single_enrollment", with(validRow(), "program_enrollment_id", null));
        expectViolation("ck_student_discount_period_range", with(validRow(), "to_period", java.time.LocalDate.of(2027, 3, 1)));
        expectViolation("ck_student_discount_scholarship_monthly", with(validRow(), "applicable_invoice_type", "ENROLLMENT_FEE"));
        expectViolation("ck_student_discount_scholarship_has_definition", with(validRow(), "discount_definition_id", null));
        expectViolation("ck_student_discount_individual_no_definition", with(validRow(), "discount_type", "INDIVIDUAL"));
        expectViolation("ck_student_discount_cap_currency", with(validRow(), "cap_per_invoice", 10));
        expectViolation("ck_student_discount_revocation", with(validRow(), "status", "REVOKED"));
        expectViolation("ck_student_discount_revocation", with(validRow(), "revoked_at", java.time.OffsetDateTime.parse("2027-04-10T10:00:00Z")));
        expectViolation("student_discount_discount_type_check", with(validRow(), "discount_type", "MANUAL"));
        expectViolation("student_discount_status_check", with(validRow(), "status", "PROPOSED"));
        expectViolation("student_discount_priority_check", with(validRow(), "priority", 0));
        expectViolation("reason", with(validRow(), "reason", null));
    }

    @Test
    void installmentsOnlyBelongToMonthlyInstallmentDiscountsAndAnExhaustedRowNeedsThem() {
        expectViolation("ck_student_discount_installments_monthly",
                with(validRow(), "discount_type", "INDIVIDUAL", "discount_definition_id", null, "applicable_invoice_type", "ENROLLMENT_FEE", "installments_total", 3));
        expectViolation("ck_student_discount_exhausted_has_installments", with(validRow(), "status", "EXHAUSTED"));
        transactionTemplate.executeWithoutResult(status -> {
            assertThat(insert(with(validRow(), "installments_total", 3))).isPositive();
            assertThat(insert(with(validRow(), "installments_total", 3, "status", "EXHAUSTED"))).isPositive();
            status.setRollbackOnly();
        });
    }

    @Test
    void aRevokedRowNeedsEveryRevocationField() {
        Map<String, Object> complete = with(validRow(), "status", "REVOKED", "revoked_at", java.time.OffsetDateTime.parse("2027-04-10T10:00:00Z"),
                "revoked_by_id", offering.directorId(), "revocation_reason", "motivo", "revoked_from_period", java.time.LocalDate.of(2027, 5, 1));
        transactionTemplate.executeWithoutResult(status -> {
            assertThat(insert(complete)).isPositive();
            status.setRollbackOnly();
        });
        expectViolation("ck_student_discount_revocation", with(new LinkedHashMap<>(complete), "revoked_from_period", null));
        expectViolation("ck_student_discount_revocation", with(new LinkedHashMap<>(complete), "revocation_reason", null));
    }

    @Test
    void theStatusHistoryOfAStudentDiscountCanNeverBeUpdatedNorDeleted() {
        long discount = insert(validRow());
        long change = jdbc.queryForObject("""
                insert into student_discount_status_change (student_discount_id, from_status, to_status, event, actor_process, changed_at)
                values (?, null, 'ACTIVE', 'DISCOUNT_GRANTED', 'TEST', now()) returning id
                """, Long.class, discount);

        assertThatThrownBy(() -> jdbc.update("update student_discount_status_change set reason = 'x' where id = ?", change)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("delete from student_discount_status_change where id = ?", change)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("select count(*) from student_discount_status_change where id = ?", Long.class, change)).isEqualTo(1L);
        jdbc.execute("alter table student_discount_status_change disable trigger trg_student_discount_status_change_append_only");
        jdbc.update("delete from student_discount_status_change where id = ?", change);
        jdbc.execute("alter table student_discount_status_change enable trigger trg_student_discount_status_change_append_only");
        jdbc.update("delete from student_discount where id = ?", discount);
    }

    @Test
    void anIssuedDiscountOnlyAcceptsAOneTimeConsumptionRelease() {
        long released = insertIssuedDiscount("F-" + String.format("%08d", System.nanoTime() % 100_000_000L));
        long other = insertIssuedDiscount("F-" + String.format("%08d", (System.nanoTime() + 7) % 100_000_000L));
        long mixed = insertIssuedDiscount("F-" + String.format("%08d", (System.nanoTime() + 13) % 100_000_000L));

        assertThat(jdbc.update("update invoice_discount set consumption_released_at = now() where id = ?", released)).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update("update invoice_discount set consumption_released_at = now() where id = ?", released))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("immutable");
        assertThatThrownBy(() -> jdbc.update("update invoice_discount set consumption_released_at = null where id = ?", released))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("immutable");
        assertThatThrownBy(() -> jdbc.update("update invoice_discount set discount_amount = 6 where id = ?", other))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("immutable");
        assertThatThrownBy(() -> jdbc.update("update invoice_discount set discount_amount = 6, consumption_released_at = now() where id = ?", mixed))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("immutable");
        assertThatThrownBy(() -> jdbc.update("update invoice_discount set calculation = cast('{}' as jsonb), consumption_released_at = now() where id = ?", mixed))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("immutable");
        assertThatThrownBy(() -> jdbc.update("delete from invoice_discount where id = ?", released))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("immutable");
        assertThat(jdbc.queryForObject("select discount_amount from invoice_discount where id = ?", java.math.BigDecimal.class, other)).isEqualByComparingTo("10");
        assertThat(jdbc.queryForObject("select consumption_released_at is null from invoice_discount where id = ?", Boolean.class, mixed)).isTrue();
    }
}
