package com.red_escuela.backend.billing.discount.operation;

import com.red_escuela.backend.billing.entity.CohortIntake;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingRequest;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingResult;
import com.red_escuela.backend.billing.invoice.operation.IssuingScenarioFactory;
import com.red_escuela.backend.billing.invoice.service.InvoiceIssuingService;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-287")
@Tag("D-295")
class CohortEntryAcquisitionConcurrencyIT extends AbstractCohortPromotionAcquisitionIT {
    private static final int ROUNDS = 8;
    private static final int TIMEOUT_SECONDS = 60;
    private static final YearMonth MARCH = YearMonth.of(2027, 3);
    private static final StatusChangeActor ACTOR = StatusChangeActor.ofProcess("TEST_COHORT_ENTRY_CONCURRENCY");

    @Autowired
    private InvoiceIssuingService issuingService;

    @Test
    void issuingTheEntryFeeAndAcquiringACohortPromotionOnTheSameEnrollmentNeverDeadlock() throws Exception {
        Long definitionId = publish(promotion("Cupo amplio", "10", 100));
        Long templateId = factory.newTemplate(offering, 2027);
        Long cohortId = factory.newCohort(templateId, CohortIntake.FIRST, LocalDate.of(2027, 3, 1), Percentage.ofPoints(new BigDecimal("30")));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                IssuingScenarioFactory.Learner learner = newLearnerOfCohort(cohortId);
                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch go = new CountDownLatch(1);
                List<Future<?>> racers = new ArrayList<>();
                racers.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    acquire(learner);
                    return null;
                }));
                racers.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    InvoiceIssuingResult result = issuingService.issueInvoiceInNewTransaction(entryFeeOf(learner, templateId, cohortId));
                    assertThat(result).isInstanceOf(InvoiceIssuingResult.InvoiceIssued.class);
                    return null;
                }));
                ready.await();
                go.countDown();
                for (Future<?> racer : racers) {
                    racer.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                }

                assertThat(acquisitionsOf(learner)).as("round %d acquisitions", round).hasSize(1);
                assertThat(jdbc.queryForObject("select count(*) from student_discount where program_enrollment_id = ? and origin = 'COHORT_ENTRY_CONFIGURATION'",
                        Long.class, learner.programEnrollmentId())).as("round %d entry discounts", round).isEqualTo(1L);
            }
            assertThat(placesUsed(definitionId)).isEqualTo(ROUNDS);
        } finally {
            pool.shutdownNow();
        }
    }

    private InvoiceIssuingRequest entryFeeOf(IssuingScenarioFactory.Learner learner, Long templateId, Long cohortId) {
        return new InvoiceIssuingRequest(learner.studentId(), learner.programEnrollmentId(), null, offering.programId(), null, cohortId, FeeType.ENROLLMENT_FEE,
                MARCH, MARCH.atDay(11), MARCH.atDay(1), InvoiceIssuingReason.ENROLLMENT_FEE_ON_DEMAND, learner.selectionId(), templateId, 1, true, null, null, null,
                null, null, ACTOR);
    }
}
