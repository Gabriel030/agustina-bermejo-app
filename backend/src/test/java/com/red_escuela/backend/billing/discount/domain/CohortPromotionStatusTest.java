package com.red_escuela.backend.billing.discount.domain;

import com.red_escuela.backend.billing.discount.DiscountDraftBuilder;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-432")
@Tag("D-283")
@Tag("D-433")
class CohortPromotionStatusTest {
    private static final Instant NOW = Instant.parse("2031-01-10T10:00:00Z");
    private static final LocalDate AVAILABLE_FROM = LocalDate.of(2031, 1, 1);
    private static final LocalDate WINDOW_FROM = LocalDate.of(2031, 2, 1);
    private static final LocalDate WINDOW_TO = LocalDate.of(2031, 2, 28);
    private static final LocalDate AVAILABLE_UNTIL = LocalDate.of(2031, 6, 30);

    private DiscountDefinition promotion(Integer quota) {
        return DiscountDefinition.publishDiscountDefinition(DiscountDraftBuilder.cohortPromotion().availableFrom(AVAILABLE_FROM).availableUntil(AVAILABLE_UNTIL)
                .withEnrollmentWindow(WINDOW_FROM, WINDOW_TO).withQuota(quota).build(), 7L, NOW);
    }

    private Optional<CohortPromotionStatus> statusOn(DiscountDefinition definition, LocalDate date, long placesUsed) {
        return definition.cohortPromotionStatusOn(date, placesUsed);
    }

    @Test
    void thePromotionIsUpcomingUntilTheDayBeforeTheEnrollmentWindowOpens() {
        DiscountDefinition definition = promotion(5);

        assertThat(statusOn(definition, WINDOW_FROM.minusDays(1), 0)).contains(CohortPromotionStatus.UPCOMING);
        assertThat(statusOn(definition, WINDOW_FROM, 0)).contains(CohortPromotionStatus.OPEN);
    }

    @Test
    void thePromotionIsUpcomingWhileItsValidityHasNotStartedEvenIfTheWindowAlreadyOpened() {
        DiscountDefinition definition = DiscountDefinition.publishDiscountDefinition(DiscountDraftBuilder.cohortPromotion().availableFrom(LocalDate.of(2031, 3, 1))
                .withEnrollmentWindow(WINDOW_FROM, LocalDate.of(2031, 3, 31)).build(), 7L, NOW);

        assertThat(statusOn(definition, LocalDate.of(2031, 2, 15), 0)).contains(CohortPromotionStatus.UPCOMING);
        assertThat(statusOn(definition, LocalDate.of(2031, 3, 1), 0)).contains(CohortPromotionStatus.OPEN);
    }

    @Test
    void theLastDayOfTheEnrollmentWindowIsStillOpenAndTheNextOneIsEnrollmentClosed() {
        DiscountDefinition definition = promotion(5);

        assertThat(statusOn(definition, WINDOW_TO, 0)).contains(CohortPromotionStatus.OPEN);
        assertThat(statusOn(definition, WINDOW_TO.plusDays(1), 0)).contains(CohortPromotionStatus.ENROLLMENT_CLOSED);
    }

    @Test
    void theLastDayOfTheValidityIsStillEnrollmentClosedAndTheNextOneIsFinished() {
        DiscountDefinition definition = promotion(5);

        assertThat(statusOn(definition, AVAILABLE_UNTIL, 0)).contains(CohortPromotionStatus.ENROLLMENT_CLOSED);
        assertThat(statusOn(definition, AVAILABLE_UNTIL.plusDays(1), 0)).contains(CohortPromotionStatus.FINISHED);
    }

    @Test
    void aFullQuotaIsDecidedByThePlacesUsedOfThatPromotionAlone() {
        DiscountDefinition quotaOfTwo = promotion(2);
        DiscountDefinition quotaOfFive = promotion(5);
        DiscountDefinition withoutQuota = promotion(null);
        LocalDate duringTheWindow = LocalDate.of(2031, 2, 10);

        assertThat(statusOn(quotaOfTwo, duringTheWindow, 1)).contains(CohortPromotionStatus.OPEN);
        assertThat(statusOn(quotaOfTwo, duringTheWindow, 2)).contains(CohortPromotionStatus.QUOTA_FULL);
        assertThat(statusOn(quotaOfFive, duringTheWindow, 2)).contains(CohortPromotionStatus.OPEN);
        assertThat(statusOn(withoutQuota, duringTheWindow, 1_000)).contains(CohortPromotionStatus.OPEN);
    }

    @Test
    void aFullQuotaStaysFullAfterTheWindowAndOnlyTheEndOfTheValidityFinishesIt() {
        DiscountDefinition definition = promotion(2);

        assertThat(statusOn(definition, WINDOW_TO.plusDays(1), 2)).contains(CohortPromotionStatus.QUOTA_FULL);
        assertThat(statusOn(definition, AVAILABLE_UNTIL.plusDays(1), 2)).contains(CohortPromotionStatus.FINISHED);
    }

    @Test
    void aFullQuotaDoesNotHideAnUpcomingPromotion() {
        DiscountDefinition definition = promotion(2);

        assertThat(statusOn(definition, WINDOW_FROM.minusDays(1), 2)).contains(CohortPromotionStatus.UPCOMING);
    }

    @Test
    void aClosedPromotionIsClosedWhateverTheDateOrTheQuota() {
        DiscountDefinition definition = promotion(2);
        definition.closeDiscountDefinition("cierre", StatusChangeActor.ofPerson(7L), NOW);

        assertThat(statusOn(definition, WINDOW_FROM.minusDays(1), 0)).contains(CohortPromotionStatus.CLOSED);
        assertThat(statusOn(definition, LocalDate.of(2031, 2, 10), 2)).contains(CohortPromotionStatus.CLOSED);
        assertThat(statusOn(definition, AVAILABLE_UNTIL.plusDays(1), 0)).contains(CohortPromotionStatus.CLOSED);
    }

    @Test
    void aPromotionWithoutAnEndDateNeverFinishes() {
        DiscountDefinition definition = DiscountDefinition.publishDiscountDefinition(DiscountDraftBuilder.cohortPromotion().availableFrom(AVAILABLE_FROM)
                .withEnrollmentWindow(WINDOW_FROM, WINDOW_TO).build(), 7L, NOW);

        assertThat(statusOn(definition, LocalDate.of(2040, 1, 1), 0)).contains(CohortPromotionStatus.ENROLLMENT_CLOSED);
    }

    @Test
    void definitionsOfAnyOtherTypeHaveNoPromotionStatus() {
        DiscountDefinition scholarship = DiscountDefinition.publishDiscountDefinition(DiscountDraftBuilder.scholarship().build(), 7L, NOW);
        DiscountDefinition annualUpfront = DiscountDefinition.publishDiscountDefinition(DiscountDraftBuilder.annualUpfront().build(), 7L, NOW);

        assertThat(statusOn(scholarship, LocalDate.of(2031, 3, 1), 0)).isEmpty();
        assertThat(statusOn(annualUpfront, LocalDate.of(2031, 3, 1), 0)).isEmpty();
    }

    @Test
    void aPromotionCreatedBeforeTheWindowWasRequiredIsOpenWhileValidAndFinishedAfterItWithoutFailing() {
        DiscountDefinition definition = DiscountDefinition.publishDiscountDefinition(DiscountDraftBuilder.cohortPromotion().availableFrom(AVAILABLE_FROM)
                .availableUntil(AVAILABLE_UNTIL).withEnrollmentWindow(null, null).withQuota(3).build(), 7L, NOW);

        assertThat(statusOn(definition, AVAILABLE_FROM.minusDays(1), 0)).contains(CohortPromotionStatus.UPCOMING);
        assertThat(statusOn(definition, AVAILABLE_FROM, 0)).contains(CohortPromotionStatus.OPEN);
        assertThat(statusOn(definition, AVAILABLE_UNTIL, 2)).contains(CohortPromotionStatus.OPEN);
        assertThat(statusOn(definition, AVAILABLE_UNTIL, 3)).contains(CohortPromotionStatus.QUOTA_FULL);
        assertThat(statusOn(definition, AVAILABLE_UNTIL.plusDays(1), 0)).contains(CohortPromotionStatus.FINISHED);
    }

    @Test
    void aWindowThatOpensAfterTheValidityEndsIsUpcomingUntilTheValidityEndsAndFinishedAfter() {
        DiscountDefinition definition = DiscountDefinition.publishDiscountDefinition(DiscountDraftBuilder.cohortPromotion().availableFrom(AVAILABLE_FROM)
                .availableUntil(LocalDate.of(2031, 1, 31)).withEnrollmentWindow(WINDOW_FROM, WINDOW_TO).build(), 7L, NOW);

        assertThat(statusOn(definition, LocalDate.of(2031, 1, 31), 0)).contains(CohortPromotionStatus.UPCOMING);
        assertThat(statusOn(definition, LocalDate.of(2031, 2, 1), 0)).contains(CohortPromotionStatus.FINISHED);
    }
}
