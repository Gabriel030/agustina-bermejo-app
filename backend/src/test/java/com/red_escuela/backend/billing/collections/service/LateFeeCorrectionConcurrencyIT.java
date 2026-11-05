package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.enrollment.service.CourseEnrollmentService;
import com.red_escuela.backend.enrollment.service.EnrollmentWithdrawal;
import com.red_escuela.backend.shared.time.MutableClock;
import com.red_escuela.backend.testsupport.SlowTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-375")
@SlowTest(reason = "Runs the withdrawal, the month close, the correction and the payment from concurrent threads against Postgres in several rounds")
class LateFeeCorrectionConcurrencyIT extends AbstractLateFeeCorrectionIT {

    @Autowired
    private CourseEnrollmentService courseEnrollmentService;

    private void runTogether(List<Callable<Object>> tasks, CountDownLatch start) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        try {
            List<Future<Object>> futures = tasks.stream().map(executor::submit).toList();
            start.countDown();
            for (Future<Object> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private BigDecimal netChargedDaysOfOrigin(Invoice origin) {
        return jdbc.queryForObject("select coalesce(sum(c.charged_days - coalesce((select sum(k.corrected_days) from late_fee_charge_correction k where k.late_fee_charge_id = c.id), 0)), 0) "
                + "from late_fee_charge c where c.origin_invoice_id = ?", BigDecimal.class, origin.getId());
    }

    @Test
    @Tag("D-293")
    void aWithdrawalAndTheMonthCloseOfTheSameInstallmentNeverLeaveDaysAfterTheEndChargedNorFailWithAnError() throws Exception {
        for (int round = 0; round < 3; round++) {
            int year = 2190 + round;
            arrange(year);
            publishDefaults(year);
            Invoice origin = overdueInstallmentDueOn(d(year, 4, 11));
            MutableClock.INSTANCE.setDate(d(year, 5, 1));
            CountDownLatch start = new CountDownLatch(1);

            runTogether(List.of(() -> {
                start.await();
                courseEnrollmentService.withdraw(enrollment.getId(), EnrollmentWithdrawal.voluntaryRegisteredBy(d(year, 4, 28), person.getId()));
                return null;
            }, () -> {
                start.await();
                return accrualService.reconcileLateFeesOfInvoice(origin.getId(), d(year, 5, 1));
            }), start);
            awaitEventsDrained();
            correctionStep.correctLateFeesChargedAfterTheEnrollmentEnd(d(year, 5, 2));
            awaitEventsDrained();

            assertThat(netChargedDaysOfOrigin(origin)).as("round " + round).isEqualByComparingTo("17");
            assertScenarioInvariants(d(year, 5, 2));
        }
    }

    @Test
    void theListenerAndTheSweepCorrectingTheSameDebitNoteTogetherIssueASingleCreditNote() throws Exception {
        Invoice origin = aprilDebitNoteOrigin(2194);
        Long debitNoteId = debitNoteIdOf(origin, 0);
        endEnrollmentSilently("WITHDRAWN", d(2194, 4, 28), d(2194, 5, 3));
        MutableClock.INSTANCE.setDate(d(2194, 5, 4));
        CountDownLatch start = new CountDownLatch(1);

        runTogether(List.of(() -> {
            start.await();
            return correctionService.correctLateFeesOfEnrollment(null, enrollment.getId(), d(2194, 5, 3));
        }, () -> {
            start.await();
            return correctionStep.correctLateFeesChargedAfterTheEnrollmentEnd(d(2194, 5, 4));
        }), start);
        awaitEventsDrained();

        assertThat(creditNotesOf(debitNoteId)).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from late_fee_charge_correction where debit_note_id = ?", Long.class, debitNoteId)).isEqualTo(1L);
        assertThat(netChargedDaysOf(debitNoteId)).isEqualByComparingTo("17");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-136")
    void theCorrectionAndTheFullPaymentOfTheDebitNoteTogetherLeaveItPaidWithTheSameFourHundredInTheAccount() throws Exception {
        Invoice origin = aprilDebitNoteOrigin(2195);
        Long debitNoteId = debitNoteIdOf(origin, 0);
        endEnrollmentSilently("WITHDRAWN", d(2195, 4, 28), d(2195, 5, 3));
        MutableClock.INSTANCE.setDate(d(2195, 5, 4));
        receiveMoney(origin, "103800.00", d(2195, 5, 4));
        CountDownLatch start = new CountDownLatch(1);

        runTogether(List.of(() -> {
            start.await();
            return correctionService.correctLateFeeOfDebitNote(debitNoteId, d(2195, 5, 4));
        }, () -> {
            start.await();
            allocateOn(origin, d(2195, 5, 4));
            return null;
        }), start);
        awaitEventsDrained();

        assertThat(creditNotesOf(debitNoteId)).hasSize(1);
        assertThat(jdbc.queryForObject("select payment_status from invoice where id = ?", String.class, debitNoteId)).isEqualTo("PAID");
        assertThat(jdbc.queryForObject("select unallocated_credit_amount from student_current_account where id = ?", BigDecimal.class, origin.getStudentCurrentAccountId()))
                .isEqualByComparingTo("400.00");
        invariants.assertAllBillingInvariantsHold();
    }
}
