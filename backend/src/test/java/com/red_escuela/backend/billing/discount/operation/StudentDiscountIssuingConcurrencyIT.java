package com.red_escuela.backend.billing.discount.operation;

import com.red_escuela.backend.billing.discount.domain.EnrollmentReference;
import com.red_escuela.backend.billing.discount.domain.StudentDiscount;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountStatus;
import com.red_escuela.backend.billing.discount.exception.ScholarshipStartBeforeNextInstallmentException;
import com.red_escuela.backend.billing.discount.service.EnrollmentDiscountLock;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingRequest;
import com.red_escuela.backend.billing.invoice.operation.IssuingScenarioFactory;
import com.red_escuela.backend.shared.domain.Percentage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Tag("D-238")
@Tag("D-284")
@Tag("D-405")
@Tag("D-073")
class StudentDiscountIssuingConcurrencyIT extends AbstractStudentDiscountIssuingIT {

    private static final YearMonth MARCH = YearMonth.of(2027, 3);
    private static final YearMonth APRIL = YearMonth.of(2027, 4);
    private static final YearMonth JUNE = YearMonth.of(2027, 6);
    private static final YearMonth JULY = YearMonth.of(2027, 7);
    private static final YearMonth DECEMBER = YearMonth.of(2027, 12);
    private static final int TIMEOUT_SECONDS = 60;

    @Autowired
    private EnrollmentDiscountLock enrollmentLock;

    private void awaitIssuingBlockedOnALockOrFinished(Future<Long> issuing) {
        await().atMost(Duration.ofSeconds(30)).until(() -> issuing.isDone() || jdbc.queryForObject(
                "select count(*) from pg_stat_activity where datname = current_database() and wait_event_type = 'Lock'", Long.class) > 0);
    }

    private <T> T resultOf(Future<T> future) throws Exception {
        return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    @SuppressWarnings("unchecked")
    void aGrantHoldingTheEnrollmentBeforeTheIssueOfItsStartMonthIsAppliedToThatMonthWithoutDeadlock() throws Exception {
        issueInstallment(MARCH);
        today(LocalDate.of(2027, 4, 1));
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<Long>[] april = new Future[1];
            StudentDiscount[] granted = new StudentDiscount[1];
            transactionTemplate.executeWithoutResult(status -> {
                enrollmentLock.lockDiscountsOf(enrollment());
                april[0] = pool.submit(() -> issue(installmentOf(learner, APRIL)));
                awaitIssuingBlockedOnALockOrFinished(april[0]);
                granted[0] = grantAsDirector(scholarship("50", APRIL, JUNE));
            });

            resultOf(april[0]);

            assertThat(granted[0].getStatus()).isEqualTo(StudentDiscountStatus.ACTIVE);
            assertThat(onlyDiscountOn(FeeType.MONTHLY_INSTALLMENT, APRIL).get("student_discount_id")).isEqualTo(granted[0].getId());
            invariants.assertAllBillingInvariantsHold();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void aGrantRacingTheIssueOfItsStartMonthEitherAppliesOrIsRejected() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 6; round++) {
                IssuingScenarioFactory.Learner racer = newPlainLearner();
                today(LocalDate.of(2027, 3, 1));
                issue(installmentOf(racer, MARCH));
                today(LocalDate.of(2027, 4, 1));
                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch go = new CountDownLatch(1);
                Future<Long> issuing = pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return issue(installmentOf(racer, APRIL));
                });
                Future<StudentDiscount> granting = pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    try {
                        return grantAsDirectorTo(EnrollmentReference.ofProgram(racer.programEnrollmentId()),
                                scholarship("50", APRIL, JUNE));
                    } catch (ScholarshipStartBeforeNextInstallmentException rejected) {
                        return null;
                    }
                });
                ready.await();
                go.countDown();
                resultOf(issuing);
                StudentDiscount grant = resultOf(granting);

                List<Map<String, Object>> aprilDiscounts = discountsOn(racer, FeeType.MONTHLY_INSTALLMENT, APRIL);
                if (grant == null) {
                    assertThat(aprilDiscounts).as("rejected grant, round %d", round).isEmpty();
                } else {
                    assertThat(aprilDiscounts).as("accepted grant, round %d", round).hasSize(1);
                    assertThat(aprilDiscounts.get(0).get("student_discount_id")).isEqualTo(grant.getId());
                }
            }
            invariants.assertAllBillingInvariantsHold();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void aRevocationHoldingTheDiscountRowBeforeTheIssueOfJuneNeverLetsJuneUseTheRevokedDiscount() throws Exception {
        today(LocalDate.of(2027, 6, 1));
        StudentDiscount scholarship = grantAsDirector(scholarship("50", JUNE, DECEMBER));
        today(LocalDate.of(2027, 6, 10));
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<Long>[] june = new Future[1];
            StudentDiscount[] revoked = new StudentDiscount[1];
            transactionTemplate.executeWithoutResult(status -> {
                discountRepository.findByIdForUpdate(scholarship.getId()).orElseThrow();
                june[0] = pool.submit(() -> issue(installmentOf(learner, JUNE)));
                awaitIssuingBlockedOnALockOrFinished(june[0]);
                revoked[0] = revokeAsDirector(scholarship.getId(), "Cambió la situación económica");
            });

            resultOf(june[0]);

            assertThat(revoked[0].getRevokedFromPeriod()).isEqualTo(JUNE);
            assertThat(discountsOn(FeeType.MONTHLY_INSTALLMENT, JUNE)).isEmpty();
            invariants.assertAllBillingInvariantsHold();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void aRevocationRacingAnIssueNeverAppliesARevokedMonth() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 6; round++) {
                IssuingScenarioFactory.Learner racer = newPlainLearner();
                today(LocalDate.of(2027, 6, 1));
                StudentDiscount scholarship = grantAsDirectorTo(EnrollmentReference.ofProgram(racer.programEnrollmentId()),
                        scholarship("50", JUNE, DECEMBER));
                today(LocalDate.of(2027, 6, 10));
                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch go = new CountDownLatch(1);
                Future<Long> issuing = pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return issue(installmentOf(racer, JUNE));
                });
                Future<StudentDiscount> revoking = pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return revokeAsDirector(scholarship.getId(), "Cambió la situación económica");
                });
                ready.await();
                go.countDown();
                resultOf(issuing);
                StudentDiscount revoked = resultOf(revoking);

                List<Map<String, Object>> juneDiscounts = discountsOn(racer, FeeType.MONTHLY_INSTALLMENT, JUNE);
                if (revoked.getRevokedFromPeriod().equals(JUNE)) {
                    assertThat(juneDiscounts).as("revoked from June, round %d", round).isEmpty();
                } else {
                    assertThat(revoked.getRevokedFromPeriod()).as("round %d", round).isEqualTo(JULY);
                    assertThat(juneDiscounts).as("issued before the revocation, round %d", round).hasSize(1);
                }
            }
            invariants.assertAllBillingInvariantsHold();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentIssuesOfTheSameEnrollmentDoNotDeadlock() throws Exception {
        useCohortLearner(Percentage.ofPoints(BigDecimal.ZERO));
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            for (int round = 0; round < 4; round++) {
                IssuingScenarioFactory.Learner racer = newCohortLearner();
                var target = EnrollmentReference.ofProgram(racer.programEnrollmentId());
                today(LocalDate.of(2027, 3, 1));
                StudentDiscount onFee = grantAsDirectorTo(target, individual(InvoiceType.ENROLLMENT_FEE, percent("30"), MARCH, null, "Hermano"));
                StudentDiscount scholarship = grantAsDirectorTo(target, scholarship("50", MARCH, DECEMBER));
                CountDownLatch ready = new CountDownLatch(3);
                CountDownLatch go = new CountDownLatch(1);
                List<Future<Long>> issues = new ArrayList<>();
                for (InvoiceIssuingRequest request : List.of(enrollmentFeeOf(racer, 1, MARCH, false), installmentOf(racer, MARCH), installmentOf(racer, APRIL))) {
                    issues.add(pool.submit(() -> {
                        ready.countDown();
                        go.await();
                        return issue(request);
                    }));
                }
                ready.await();
                go.countDown();
                for (Future<Long> future : issues) {
                    resultOf(future);
                }

                assertThat(discountsOn(racer, FeeType.ENROLLMENT_FEE, MARCH)).singleElement().satisfies(row -> assertThat(row.get("student_discount_id")).isEqualTo(onFee.getId()));
                assertThat(discountsOn(racer, FeeType.MONTHLY_INSTALLMENT, MARCH)).singleElement().satisfies(row -> assertThat(row.get("student_discount_id")).isEqualTo(scholarship.getId()));
                assertThat(discountsOn(racer, FeeType.MONTHLY_INSTALLMENT, APRIL)).singleElement().satisfies(row -> assertThat(row.get("student_discount_id")).isEqualTo(scholarship.getId()));
            }
            invariants.assertAllBillingInvariantsHold();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void twoEnrollmentFeesOfTheSameLectiveYearIssuedAtOnceConsumeTheIndividualDiscountOnce() throws Exception {
        useCohortLearner(Percentage.ofPoints(BigDecimal.ZERO));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 4; round++) {
                IssuingScenarioFactory.Learner racer = newCohortLearner();
                var target = EnrollmentReference.ofProgram(racer.programEnrollmentId());
                today(LocalDate.of(2027, 3, 1));
                StudentDiscount onFee = grantAsDirectorTo(target, individual(InvoiceType.ENROLLMENT_FEE, percent("30"), MARCH, null, "Hermano"));
                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch go = new CountDownLatch(1);
                List<Future<Long>> issues = new ArrayList<>();
                for (InvoiceIssuingRequest request : List.of(enrollmentFeeOf(racer, 1, MARCH, false), enrollmentFeeOf(racer, 2, YearMonth.of(2027, 8), false))) {
                    issues.add(pool.submit(() -> {
                        ready.countDown();
                        go.await();
                        return issue(request);
                    }));
                }
                ready.await();
                go.countDown();
                List<Long> invoiceIds = new ArrayList<>();
                for (Future<Long> future : issues) {
                    invoiceIds.add(resultOf(future));
                }

                assertThat(discountRowsOf(onFee.getId())).as("round %d", round).isEqualTo(1L);
                Long discountedInvoiceId = jdbc.queryForObject("select l.invoice_id from invoice_discount d join invoice_line l on l.id = d.invoice_line_id where d.student_discount_id = ?",
                        Long.class, onFee.getId());
                assertThat(discountedInvoiceId).as("the first fee to take the lock gets the discount, round %d", round).isEqualTo(invoiceIds.stream().min(Long::compare).orElseThrow());
            }
            invariants.assertAllBillingInvariantsHold();
        } catch (ExecutionException failure) {
            throw new AssertionError(failure.getCause());
        } finally {
            pool.shutdownNow();
        }
    }
}
