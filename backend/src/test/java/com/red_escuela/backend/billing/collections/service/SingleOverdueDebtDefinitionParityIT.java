package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.account.service.AbstractCurrentAccountIT;
import com.red_escuela.backend.billing.collections.exception.OverdueDebtBlocksNewEnrollmentException;
import com.red_escuela.backend.evaluation.api.PaymentsStanding;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;
import com.red_escuela.backend.graduation.certificate.regular.exception.StudentHasDebtException;
import com.red_escuela.backend.graduation.certificate.regular.specialist.RegularStandingGate;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.time.MutableClock;
import com.red_escuela.backend.testsupport.SlowTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-065")
@Tag("D-288")
@Tag("D-331")
@SlowTest(reason = "Boots the first Spring context of its JVM when run alone, so its time includes the one-time start-up")
class SingleOverdueDebtDefinitionParityIT extends AbstractCurrentAccountIT {

    private static final LocalDate TODAY = LocalDate.of(2027, 3, 24);
    private static final int GRACE_DAYS = 5;
    private static final RegularStandingGate.StandingConfirmation NOT_CONFIRMED = new RegularStandingGate.StandingConfirmation(false, null);

    private record Receipt(String status, int uploadedDaysAfterDue) {
    }

    private record Scenario(String name, Currency currency, String paymentStatus, int dueDaysAgo, String total, String credited, List<Receipt> receipts,
                            boolean withFreshReceiptSibling, boolean expectedDebt) {
    }

    @Autowired
    private RegularStandingGate regularStandingGate;

    @Autowired
    private StudentPaymentsStandingPortAdapter closurePort;

    @Autowired
    private StudentDebtEnrollmentGuard enrollmentGuard;

    @Autowired
    private StudentOverdueDebtFinder overdueDebtFinder;

    private Long graceVersionId;

    @AfterEach
    void restoreClockAndConfiguration() {
        MutableClock.INSTANCE.reset();
        if (graceVersionId != null) {
            jdbc.update("delete from configuration_version where id = ?", graceVersionId);
        }
    }

    private void publishGracePeriodOf(int days) {
        Long staffId = inTransaction(() -> newStaffPerson().getId());
        graceVersionId = jdbc.queryForObject("""
                insert into configuration_version (scope, topic_key, config_values, status, effective_from, change_reason, proposed_by_id, proposed_at, decided_by_id, decided_at,
                                                   direct_publication)
                values ('LATE_PAYMENT', 'LATE_PAYMENT', cast(? as jsonb), 'EFFECTIVE', ?, 'Single definition parity', ?, now(), ?, now(), true)
                returning id
                """, Long.class,
                "{\"type\":\"LATE_PAYMENT\",\"gracePeriodDays\":" + days + ",\"dailyLateFeeRate\":{\"fraction\":0.002},\"suspensionAfterDays\":30,"
                        + "\"suspensionAfterInvoiceCount\":2,\"blockNewEnrollmentsWhenOverdue\":true,\"lateFeeDebitNoteDueDays\":10,"
                        + "\"freezeLateFeesOnWithdrawal\":true,\"lateFeeChargingEnabled\":false}",
                Date.valueOf(TODAY.minusYears(1)), staffId, staffId);
    }

    private static Scenario scenario(String name, String paymentStatus, int dueDaysAgo, boolean expectedDebt) {
        return new Scenario(name, Currency.ARS, paymentStatus, dueDaysAgo, "1000.00", "0.00", List.of(), false, expectedDebt);
    }

    private static Scenario withReceipts(String name, int dueDaysAgo, boolean expectedDebt, Receipt... receipts) {
        return new Scenario(name, Currency.ARS, "PAYMENT_UNDER_REVIEW", dueDaysAgo, "1000.00", "0.00", List.of(receipts), false, expectedDebt);
    }

    private List<Scenario> scenarios() {
        int pastGrace = GRACE_DAYS + 1;
        return List.of(
                scenario("pending inside the grace period", "PENDING", GRACE_DAYS - 2, false),
                scenario("pending on the last day of grace", "PENDING", GRACE_DAYS, false),
                scenario("pending the day after the grace", "PENDING", pastGrace, true),
                scenario("pending not due yet", "PENDING", -3, false),
                scenario("marked overdue inside the grace period", "OVERDUE", 2, true),
                scenario("paid past grace", "PAID", pastGrace, false),
                scenario("cancelled past grace", "CANCELLED", pastGrace, false),
                withReceipts("fresh receipt uploaded before the due date", pastGrace, false, new Receipt("PENDING_REVIEW", -1)),
                withReceipts("stale receipt uploaded long before the due date", 60, true, new Receipt("PENDING_REVIEW", -80)),
                new Scenario("stale receipt invoice next to a fresh receipt invoice", Currency.ARS, "PAYMENT_UNDER_REVIEW", 60, "1000.00", "0.00",
                        List.of(new Receipt("PENDING_REVIEW", -80)), true, true),
                withReceipts("receipt uploaded after the due date", pastGrace, true, new Receipt("PENDING_REVIEW", 1)),
                withReceipts("rejected receipt only", pastGrace, true, new Receipt("REJECTED", -1)),
                withReceipts("approved receipt only", pastGrace, true, new Receipt("APPROVED", -1)),
                new Scenario("fee without enrollment line past grace", Currency.ARS, "PENDING", pastGrace, "1000.00", "0.00", List.of(), false, true),
                new Scenario("fee without enrollment line inside grace", Currency.ARS, "PENDING", 2, "1000.00", "0.00", List.of(), false, false),
                new Scenario("partially credited past grace", Currency.ARS, "PENDING", pastGrace, "1000.00", "400.00", List.of(), false, true),
                new Scenario("usd pending past grace", Currency.USD, "PENDING", pastGrace, "30.00", "0.00", List.of(), false, true),
                new Scenario("usd pending on the last day of grace", Currency.USD, "PENDING", GRACE_DAYS, "30.00", "0.00", List.of(), false, false),
                new Scenario("usd partially credited past grace", Currency.USD, "PENDING", pastGrace, "30.00", "20.00", List.of(), false, true));
    }

    @Test
    void certificateGateClosureStandingAndEnrollmentGuardAgreeOnEveryScenario() {
        MutableClock.INSTANCE.setDate(TODAY);
        publishGracePeriodOf(GRACE_DAYS);
        Map<Long, Scenario> byStudent = new LinkedHashMap<>();
        for (Scenario scenario : scenarios()) {
            byStudent.put(seed(scenario), scenario);
        }
        Long studentWithoutInvoices = newStudentId();
        List<Long> allStudents = new ArrayList<>(byStudent.keySet());
        allStudents.add(studentWithoutInvoices);

        Map<Long, PaymentsStanding> closureStandings = closurePort.findPaymentsStanding(allStudents, TODAY);

        byStudent.forEach((studentId, scenario) -> {
            assertThat(certificateGateBlocks(studentId)).as("certificate gate: " + scenario.name()).isEqualTo(scenario.expectedDebt());
            assertThat(closureStandings.get(studentId) == PaymentsStanding.NOT_OK).as("closure standing: " + scenario.name()).isEqualTo(scenario.expectedDebt());
            assertThat(enrollmentGuardBlocks(studentId)).as("enrollment guard: " + scenario.name()).isEqualTo(scenario.expectedDebt());
            assertThat(overdueDebtFinder.hasOverdueDebt(studentId, TODAY)).as("finder: " + scenario.name()).isEqualTo(scenario.expectedDebt());
        });
        assertThat(certificateGateBlocks(studentWithoutInvoices)).isFalse();
        assertThat(closureStandings.get(studentWithoutInvoices)).isEqualTo(PaymentsStanding.OK);
        assertThat(enrollmentGuardBlocks(studentWithoutInvoices)).isFalse();
    }

    private boolean certificateGateBlocks(Long studentId) {
        try {
            regularStandingGate.resolve(studentId, RegularityKind.COURSE, null, NOT_CONFIRMED);
            return false;
        } catch (StudentHasDebtException exception) {
            return true;
        }
    }

    private boolean enrollmentGuardBlocks(Long studentId) {
        try {
            enrollmentGuard.verifyNoOutstandingDebt(studentId);
            return false;
        } catch (OverdueDebtBlocksNewEnrollmentException exception) {
            return true;
        }
    }

    private Long seed(Scenario scenario) {
        Long studentId = newStudentId();
        Long accountId = newAccountId(studentId, scenario.currency());
        if (scenario.withFreshReceiptSibling()) {
            seedInvoice(accountId, withReceipts("fresh sibling", GRACE_DAYS + 1, false, new Receipt("PENDING_REVIEW", -1)));
        }
        seedInvoice(accountId, scenario);
        return studentId;
    }

    private void seedInvoice(Long accountId, Scenario scenario) {
        LocalDate dueDate = TODAY.minusDays(scenario.dueDaysAgo());
        Long invoiceId = newIssuedInvoiceId(accountId, scenario.currency(), scenario.total());
        jdbc.update("update invoice set issue_date = ?, due_date = ? where id = ?", Date.valueOf(dueDate.minusDays(30)), Date.valueOf(dueDate), invoiceId);
        switch (scenario.paymentStatus()) {
            case "PAID" -> jdbc.update("update invoice set payment_status = 'PAID', paid_amount = total, outstanding_amount = 0 where id = ?", invoiceId);
            case "CANCELLED" -> jdbc.update("update invoice set payment_status = 'CANCELLED', credited_amount = total, outstanding_amount = 0 where id = ?", invoiceId);
            default -> jdbc.update("update invoice set payment_status = ?, credited_amount = ?::numeric, outstanding_amount = total - ?::numeric where id = ?",
                    scenario.paymentStatus(), scenario.credited(), scenario.credited(), invoiceId);
        }
        for (Receipt receipt : scenario.receipts()) {
            jdbc.update("insert into bank_transfer_receipt (invoice_id, file_path, uploaded_at, status) values (?, 'receipts/parity.pdf', ?, ?)",
                    invoiceId, Timestamp.valueOf(dueDate.plusDays(receipt.uploadedDaysAfterDue()).atTime(12, 0)), receipt.status());
        }
    }
}
