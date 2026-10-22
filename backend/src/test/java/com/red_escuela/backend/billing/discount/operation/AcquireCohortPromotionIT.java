package com.red_escuela.backend.billing.discount.operation;

import com.red_escuela.backend.billing.discount.exception.CohortPromotionAlreadyAcquiredException;
import com.red_escuela.backend.billing.discount.service.DiscountQuotaUsageReader;
import com.red_escuela.backend.billing.invoice.operation.IssuingScenarioFactory;
import com.red_escuela.backend.shared.domain.Currency;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@Tag("D-240")
@Tag("D-295")
@Tag("D-304")
@Tag("D-386")
class AcquireCohortPromotionIT extends AbstractCohortPromotionAcquisitionIT {

    @Autowired
    private DiscountQuotaUsageReader quotaUsageReader;

    @Test
    void thirdStudentGetsNoPromotionOnceTheQuotaOfTwoIsTakenAndTheDirectorIsWarnedOnce() {
        Long definitionId = publish(promotion("Cohorte 2027", "10", 2));
        IssuingScenarioFactory.Learner first = newLearner();
        IssuingScenarioFactory.Learner second = newLearner();
        IssuingScenarioFactory.Learner third = newLearner();
        assertThat(placesUsed(definitionId)).isZero();
        assertThat(quotaExhaustionWasNotified(definitionId)).isFalse();

        acquire(first);
        assertThat(quotaExhaustedNoticesFor(definitionId)).isEmpty();
        acquire(second);
        acquire(third);

        assertThat(acquisitionsOf(first)).hasSize(1);
        assertThat(acquisitionsOf(second)).hasSize(1);
        assertThat(acquisitionsOf(third)).isEmpty();
        assertThat(acquisitionsOf(first).get(0)).containsEntry("status", "ACTIVE").containsEntry("discount_type", "COHORT_PROMOTION")
                .containsEntry("discount_definition_id", definitionId);
        assertThat(acquisitionsOf(second).get(0)).containsEntry("status", "ACTIVE");
        assertThat(placesUsed(definitionId)).isEqualTo(2);
        assertThat(quotaUsageReader.countPlacesUsed(definitionId)).isEqualTo(2);
        assertThat(quotaExhaustionWasNotified(definitionId)).isTrue();
        await().atMost(Duration.ofSeconds(20)).during(Duration.ofSeconds(1)).untilAsserted(() -> assertThat(quotaExhaustedNoticesFor(definitionId)).hasSize(1));
    }

    @Test
    void withTheMostFavorablePromotionOutOfPlacesTheStudentFallsToTheNextEligibleOne() {
        Long bestId = publish(promotion("Cohorte 20", "20", 1));
        Long nextId = publish(promotion("Cohorte 10", "10", 5));
        IssuingScenarioFactory.Learner first = newLearner();
        IssuingScenarioFactory.Learner second = newLearner();

        acquire(first);
        assertThat(acquisitionsOf(first)).extracting(row -> row.get("discount_definition_id")).containsExactly(bestId);
        acquire(second);

        assertThat(acquisitionsOf(second)).extracting(row -> row.get("discount_definition_id")).containsExactly(nextId);
        assertThat(placesUsed(bestId)).isEqualTo(1);
        assertThat(placesUsed(nextId)).isEqualTo(1);
        assertThat(quotaExhaustionWasNotified(bestId)).isTrue();
        assertThat(quotaExhaustionWasNotified(nextId)).isFalse();
    }

    @Test
    void whenSeveralPromotionsQualifyOnlyTheMostFavorableOneTakesAPlace() {
        Long bestId = publish(promotion("Cohorte 20", "20", 10));
        Long otherId = publish(promotion("Cohorte 10", "10", 10));
        IssuingScenarioFactory.Learner learner = newLearner();
        assertThat(placesUsed(bestId)).isZero();
        assertThat(placesUsed(otherId)).isZero();

        acquire(learner);

        assertThat(acquisitionsOf(learner)).extracting(row -> row.get("discount_definition_id")).containsExactly(bestId);
        assertThat(placesUsed(bestId)).isEqualTo(1);
        assertThat(placesUsed(otherId)).isZero();
    }

    @Test
    void aFixedAmountBeatsAPercentageWhenItSavesMorePesosOnTheFinalPrice() {
        Long percentId = publish(promotion("Cohorte 10 por ciento", "10", null));
        Long fixedId = publish(promotion("Cohorte fija", "10", null).withFixedAmount("9000", Currency.ARS));
        IssuingScenarioFactory.Learner learner = newLearner();

        acquire(learner);

        assertThat(acquisitionsOf(learner)).extracting(row -> row.get("discount_definition_id")).containsExactly(fixedId);
        assertThat(placesUsed(percentId)).isZero();
    }

    @Test
    void aTieInPesosIsBrokenByThePriorityOfTheDefinition() {
        Long lowPriorityId = publish(promotion("Cohorte prioridad 2", "10", null).withPriority(2));
        Long highPriorityId = publish(promotion("Cohorte prioridad 1", "10", null).withPriority(1));
        IssuingScenarioFactory.Learner learner = newLearner();

        acquire(learner);

        assertThat(acquisitionsOf(learner)).extracting(row -> row.get("discount_definition_id")).containsExactly(highPriorityId);
        assertThat(placesUsed(lowPriorityId)).isZero();
    }

    @Test
    void aStudentWhoAlreadyHasACohortPromotionCannotAcquireAnotherOne() {
        Long firstId = publish(promotion("Cohorte 10", "10", 5));
        IssuingScenarioFactory.Learner learner = newLearner();
        acquire(learner);
        Long secondId = publish(promotion("Cohorte 30", "30", 5));
        assertThat(acquisitionsOf(learner)).hasSize(1);

        assertThatThrownBy(() -> acquire(learner)).isInstanceOf(CohortPromotionAlreadyAcquiredException.class)
                .satisfies(rejected -> assertThat(((CohortPromotionAlreadyAcquiredException) rejected).violations().get(0).code()).isEqualTo("COHORT_PROMOTION_ALREADY_ACQUIRED"));

        assertThat(acquisitionsOf(learner)).extracting(row -> row.get("discount_definition_id")).containsExactly(firstId);
        assertThat(placesUsed(secondId)).isZero();
        assertThat(placesUsed(firstId)).isEqualTo(1);
    }

    @Test
    void aPromotionWhoseEnrollmentWindowDoesNotContainTheEnrollmentDateIsNotAcquired() {
        Long definitionId = publish(promotion("Cohorte marzo", "10", 2).withEnrollmentWindow(LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 31)));
        IssuingScenarioFactory.Learner learner = newLearner();

        acquire(learner);

        assertThat(acquisitionsOf(learner)).isEmpty();
        assertThat(placesUsed(definitionId)).isZero();
    }

    @Test
    void aPromotionOfAnotherProgramIsNotAcquired() {
        IssuingScenarioFactory.Offering otherProgram = factory.newProgram(Currency.ARS, 80000, 50000);
        Long definitionId = publish(promotion("Otra carrera", "10", 2).forProgram(otherProgram.programId()));
        IssuingScenarioFactory.Learner learner = newLearner();

        acquire(learner);

        assertThat(acquisitionsOf(learner)).isEmpty();
        assertThat(placesUsed(definitionId)).isZero();
        assertThat(quotaExhaustionWasNotified(definitionId)).isFalse();
    }

    @Test
    void aPromotionThatIsNotYetAvailableOnTheEnrollmentDateIsNotAcquired() {
        Long definitionId = publish(promotion("Cohorte futura", "10", 2).availableFrom(LocalDate.of(2027, 2, 15)));
        IssuingScenarioFactory.Learner learner = newLearner();

        acquire(learner);

        assertThat(acquisitionsOf(learner)).isEmpty();
        assertThat(placesUsed(definitionId)).isZero();
    }

    @Test
    void aClosedPromotionIsNotAcquired() {
        Long definitionId = publish(promotion("Cohorte cerrada", "10", 2));
        jdbc.update("update discount_definition set status = 'CLOSED', closed_at = now(), closed_by_id = ?, closure_reason = 'cierre' where id = ?", director.getId(), definitionId);
        IssuingScenarioFactory.Learner learner = newLearner();

        acquire(learner);

        assertThat(acquisitionsOf(learner)).isEmpty();
    }

    @Test
    void aPromotionWithoutQuotaIsAcquiredByEveryoneAndNeverWarnsTheDirector() {
        Long definitionId = publish(promotion("Cohorte sin cupo", "10", null));
        IssuingScenarioFactory.Learner first = newLearner();
        IssuingScenarioFactory.Learner second = newLearner();

        acquire(first);
        acquire(second);

        assertThat(placesUsed(definitionId)).isEqualTo(2);
        assertThat(quotaExhaustionWasNotified(definitionId)).isFalse();
        await().atMost(Duration.ofSeconds(5)).during(Duration.ofSeconds(1)).untilAsserted(() -> assertThat(quotaExhaustedNoticesFor(definitionId)).isEmpty());
    }

    @Test
    void theAcquiredStudentDiscountCopiesTheDefinitionAndRecordsTheActor() {
        Long definitionId = publish(promotion("Cohorte 10", "10", 3).withPriority(4));
        IssuingScenarioFactory.Learner learner = newLearner();

        acquire(learner);

        Map<String, Object> row = jdbc.queryForMap("""
                select applicable_invoice_type, priority, installments_total, granted_by_id, origin, calculation::text as calculation, program_enrollment_id,
                       student_id, currency, from_period, to_period
                from student_discount where discount_definition_id = ?""", definitionId);
        assertThat(row).containsEntry("applicable_invoice_type", "MONTHLY_INSTALLMENT").containsEntry("priority", 4).containsEntry("installments_total", 3)
                .containsEntry("granted_by_id", director.getId()).containsEntry("origin", "COHORT_ACQUISITION")
                .containsEntry("program_enrollment_id", learner.programEnrollmentId()).containsEntry("student_id", learner.studentId()).containsEntry("currency", "ARS");
        assertThat(row.get("calculation").toString()).contains("10");
        assertThat(row.get("from_period").toString()).isEqualTo("2027-02-01");
        assertThat(row.get("to_period").toString()).isEqualTo("2027-04-01");
    }
}
