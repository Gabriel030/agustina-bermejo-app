package com.red_escuela.backend.billing.annualupfront.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.red_escuela.backend.billing.annualupfront.exception.AnnualUpfrontPlanCannotBeChangedException;
import com.red_escuela.backend.billing.annualupfront.service.AnnualUpfrontDeadlineExpirer;
import com.red_escuela.backend.billing.annualupfront.service.AnnualUpfrontExpiryResult;
import com.red_escuela.backend.billing.annualupfront.service.AnnualUpfrontPlanChangeGuard;
import com.red_escuela.backend.billing.discount.domain.EnrollmentReference;
import com.red_escuela.backend.billing.discount.service.AnnualUpfrontCoverageReader;
import com.red_escuela.backend.billing.service.InvoicePlanningContextLoader;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@AutoConfigureMockMvc
@Tag("D-155")
@Tag("D-168")
@Tag("D-158")
@Tag("D-160")
class AnnualUpfrontDeadlineExpiryIT extends AbstractAnnualUpfrontRequestIT {
    @Autowired
    private AnnualUpfrontDeadlineExpirer expirer;
    @Autowired
    private AnnualUpfrontPlanChangeGuard planChangeGuard;
    @Autowired
    private AnnualUpfrontCoverageReader coverageReader;
    @Autowired
    private InvoicePlanningContextLoader planningLoader;
    @Autowired
    private ProgramEnrollmentRepository enrollmentRepository;

    private JsonNode requestTheYear() throws Exception {
        return objectMapper.readTree(request(true, "v1", UUID.randomUUID().toString(), studentToken(), 201).getResponse().getContentAsString());
    }

    private boolean covers(EnrollmentReference enrollment, YearMonth from, YearMonth to) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> coverageReader.annualUpfrontCoversAnyMonthOf(enrollment, from, to)));
    }

    private long accountEntriesOfInvoice(long invoiceId) {
        return count("select count(*) from current_account_entry where invoice_id = " + invoiceId);
    }

    @Test
    void aRequestWithoutPaymentIsDiscardedWhenTheDeadlinePassesAndReleasesEverythingItHeld() throws Exception {
        publishAnnualPromotion();
        JsonNode requested = requestTheYear();
        long requestId = requested.get("id").asLong();
        long invoiceId = requested.get("invoiceId").asLong();
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2027, 3, 4, 10, 1));

        AnnualUpfrontExpiryResult result = expirer.expireRequestsPastDeadline();

        assertThat(result.expired()).as(result.toString() + " deadline=" + jdbc.queryForObject("select payment_deadline from invoice where id = " + invoiceId, java.sql.Timestamp.class)).isEqualTo(1);
        assertThat(count("select count(*) from annual_upfront_request where id = " + requestId + " and status = 'DISCARDED' and discard_reason = 'DEADLINE_EXPIRED' and decided_at is not null"))
                .isEqualTo(1);
        assertThat(count("select count(*) from invoice where id = " + invoiceId + " and document_status = 'DISCARDED'")).isEqualTo(1);
        assertThat(count("select count(*) from invoice_line where invoice_id = " + invoiceId + " and charge_key_released_at is null")).isZero();
        assertThat(count("select count(*) from invoice_discount where invoice_line_id in (select id from invoice_line where invoice_id = " + invoiceId + ") and consumption_released_at is null"))
                .isZero();
        assertThat(count("select count(*) from student_discount where origin = 'ANNUAL_UPFRONT_REQUEST' and status = 'ACTIVE' and program_enrollment_id = " + learner.programEnrollmentId())).isZero();
        assertThat(count("select count(*) from student_discount where origin = 'ANNUAL_UPFRONT_REQUEST' and status = 'REVOKED' and program_enrollment_id = " + learner.programEnrollmentId()))
                .isEqualTo(1);
        assertThat(count("select count(*) from invoice_status_change where invoice_id = " + invoiceId + " and to_status = 'DISCARDED'")).isEqualTo(1);
        assertThat(accountEntriesOfInvoice(invoiceId)).isZero();
    }

    @Test
    void aRequestIsNotDiscardedUntilTheDeadlineIsStrictlyPassed() throws Exception {
        JsonNode requested = requestTheYear();
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2027, 3, 4, 10, 0));

        AnnualUpfrontExpiryResult atTheDeadline = expirer.expireRequestsPastDeadline();

        assertThat(atTheDeadline.expired()).isZero();
        assertThat(count("select count(*) from annual_upfront_request where id = " + requested.get("id").asLong() + " and status = 'AWAITING_PAYMENT'")).isEqualTo(1);
    }

    @Test
    void aRequestWithAPaymentUnderReviewIsNeverTouchedByTheExpiry() throws Exception {
        JsonNode requested = requestTheYear();
        jdbc.update("update annual_upfront_request set status = 'PAYMENT_UNDER_REVIEW' where id = ?", requested.get("id").asLong());
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2027, 3, 9, 10, 0));

        AnnualUpfrontExpiryResult result = expirer.expireRequestsPastDeadline();

        assertThat(result.expired()).isZero();
        assertThat(count("select count(*) from annual_upfront_request where id = " + requested.get("id").asLong() + " and status = 'PAYMENT_UNDER_REVIEW' and discard_reason is null")).isEqualTo(1);
        assertThat(count("select count(*) from invoice where id = " + requested.get("invoiceId").asLong() + " and document_status = 'AWAITING_PAYMENT'")).isEqualTo(1);
    }

    @Test
    void runningTheExpiryTwiceOrFromTwoInstancesAtOnceDiscardsOnlyOnce() throws Exception {
        publishAnnualPromotion();
        JsonNode requested = requestTheYear();
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2027, 3, 4, 10, 1));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<AnnualUpfrontExpiryResult> run = () -> {
                start.await();
                return expirer.expireRequestsPastDeadline();
            };
            Future<AnnualUpfrontExpiryResult> first = pool.submit(run);
            Future<AnnualUpfrontExpiryResult> second = pool.submit(run);
            start.countDown();
            AnnualUpfrontExpiryResult a = first.get();
            AnnualUpfrontExpiryResult b = second.get();

            assertThat(a.expired() + b.expired()).isEqualTo(1);
            assertThat(a.failedRequestIds()).isEmpty();
            assertThat(b.failedRequestIds()).isEmpty();
        } finally {
            pool.shutdownNow();
        }
        assertThat(expirer.expireRequestsPastDeadline().expired()).isZero();
        assertThat(count("select count(*) from invoice_status_change where invoice_id = " + requested.get("invoiceId").asLong() + " and to_status = 'DISCARDED'")).isEqualTo(1);
        assertThat(count("select count(*) from student_discount where origin = 'ANNUAL_UPFRONT_REQUEST' and status = 'REVOKED' and program_enrollment_id = " + learner.programEnrollmentId()))
                .isEqualTo(1);
    }

    @Test
    void whileTheRequestWaitsTheMonthlyPlanSkipsItsMonthsAndAfterTheExpiryPlansThemAgain() throws Exception {
        requestTheYear();
        ProgramEnrollment enrollment = enrollmentRepository.findById(learner.programEnrollmentId()).orElseThrow();

        var waiting = transactionTemplate.execute(status -> planningLoader.load(enrollment, YearMonth.of(2027, 3), LocalDate.of(2027, 3, 2)).facts());

        assertThat(waiting.annualUpfrontAwaitingPaymentPeriods()).contains(YearMonth.of(2027, 3), YearMonth.of(2027, 12)).hasSize(10);

        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2027, 3, 4, 10, 1));
        expirer.expireRequestsPastDeadline();

        var afterExpiry = transactionTemplate.execute(status -> planningLoader.load(enrollment, YearMonth.of(2027, 3), LocalDate.of(2027, 3, 4)).facts());
        assertThat(afterExpiry.annualUpfrontAwaitingPaymentPeriods()).isEmpty();
    }

    @Test
    void aConfirmedAnnualUpfrontBlocksChangingThePlanOnlyInItsLectiveYear() throws Exception {
        JsonNode requested = requestTheYear();
        planChangeGuard.requirePlanChangeAllowed(learner.programEnrollmentId());
        jdbc.update("update annual_upfront_request set status = 'CONFIRMED' where id = ?", requested.get("id").asLong());

        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2027, 6, 10, 9, 0));
        assertThatThrownBy(() -> planChangeGuard.requirePlanChangeAllowed(learner.programEnrollmentId())).isInstanceOf(AnnualUpfrontPlanCannotBeChangedException.class);

        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2028, 3, 1, 9, 0));
        planChangeGuard.requirePlanChangeAllowed(learner.programEnrollmentId());
    }

    @Test
    void aRequestWaitingOrConfirmedCoversItsMonthsForTheScholarshipRuleAndADiscardedOneDoesNot() throws Exception {
        JsonNode requested = requestTheYear();
        EnrollmentReference enrollment = EnrollmentReference.ofProgram(learner.programEnrollmentId());

        assertThat(covers(enrollment, YearMonth.of(2027, 8), YearMonth.of(2027, 9))).isTrue();
        assertThat(covers(enrollment, YearMonth.of(2028, 3), YearMonth.of(2028, 4))).isFalse();
        assertThat(covers(enrollment, YearMonth.of(2027, 1), YearMonth.of(2027, 2))).isFalse();

        jdbc.update("update annual_upfront_request set status = 'CONFIRMED' where id = ?", requested.get("id").asLong());
        assertThat(covers(enrollment, YearMonth.of(2027, 12), YearMonth.of(2027, 12))).isTrue();

        jdbc.update("update annual_upfront_request set status = 'PAYMENT_UNDER_REVIEW' where id = ?", requested.get("id").asLong());
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2027, 3, 9, 10, 0));
        jdbc.update("update annual_upfront_request set status = 'AWAITING_PAYMENT' where id = ?", requested.get("id").asLong());
        expirer.expireRequestsPastDeadline();
        assertThat(covers(enrollment, YearMonth.of(2027, 8), YearMonth.of(2027, 9))).isFalse();
    }
}
