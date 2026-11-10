package com.red_escuela.backend.billing.note.service;

import com.red_escuela.backend.billing.note.service.SystemAdmissionInvoiceCancellation.Outcome;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.testsupport.SlowTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Tag("D-457")
@SlowTest(reason = "Holds the enrollment lock in one transaction while a system cancellation runs in another against Postgres")
class DiscountedInvoiceCancellationLockOrderIT extends AbstractInvoiceCancellationIT {

    @Autowired
    private SystemAdmissionInvoiceCancellation systemCancellation;

    @Test
    void cancellingAnInvoiceWithADiscountTakesTheEnrollmentLockBeforeTheAccountLockLikeTheMonthlyRunSoNeitherDeadlocks() throws Exception {
        Long studentId = newStudentId();
        Long accountId = newAccountId(studentId, Currency.ARS);
        DiscountedInvoice discounted = issueDiscountedAnnualInvoiceOfNewEnrollment(studentId, accountId);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch enrollmentLocked = new CountDownLatch(1);
            Future<Void> monthlyRunLike = executor.submit(() -> {
                transactionTemplate.executeWithoutResult(status -> {
                    jdbc.queryForList("select id from program_enrollment where id = ? for update", Long.class, discounted.programEnrollmentId());
                    enrollmentLocked.countDown();
                    await().atMost(Duration.ofSeconds(30)).until(() -> jdbc.queryForObject(
                            "select count(*) from pg_stat_activity where datname = current_database() and wait_event_type = 'Lock' and pid <> pg_backend_pid() "
                                    + "and query ilike '%program_enrollment%'", Long.class) > 0);
                    service.lockStudentCurrentAccount(accountId);
                });
                return null;
            });
            assertThat(enrollmentLocked.await(30, TimeUnit.SECONDS)).isTrue();
            Future<Outcome> cancellation = executor.submit(() -> inTransaction(() -> systemCancellation.cancelWhenUnpaid(discounted.invoiceId())));
            monthlyRunLike.get(60, TimeUnit.SECONDS);
            assertThat(cancellation.get(60, TimeUnit.SECONDS)).isEqualTo(Outcome.CANCELLED);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
    }
}
