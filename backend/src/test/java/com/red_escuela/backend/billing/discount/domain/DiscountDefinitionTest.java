package com.red_escuela.backend.billing.discount.domain;

import com.red_escuela.backend.billing.discount.DiscountDraftBuilder;
import com.red_escuela.backend.billing.discount.exception.IllegalDiscountDefinitionTransitionException;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.shared.pricing.OfferingReference;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-217")
@Tag("D-223")
@Tag("D-240")
@Tag("D-283")
@Tag("D-036")
class DiscountDefinitionTest {
    private static final Instant NOW = Instant.parse("2031-03-10T10:00:00Z");
    private static final NonNegativeMoney PRICE = NonNegativeMoney.of(new BigDecimal("100000.00"), Currency.ARS);

    @Test
    void aDirectorPublicationIsActiveAndMarkedAsDirect() {
        DiscountDefinition definition = DiscountDefinition.publishDiscountDefinition(DiscountDraftBuilder.cohortPromotion().named("  Promo marzo  ").build(), 7L, NOW);

        assertThat(definition.getStatus()).isEqualTo(DiscountDefinitionStatus.ACTIVE);
        assertThat(definition.isDirectPublication()).isTrue();
        assertThat(definition.getAuthorizationRequestId()).isNull();
        assertThat(definition.getCreatedById()).isEqualTo(7L);
        assertThat(definition.getCreatedAt()).isEqualTo(NOW);
        assertThat(definition.getName()).isEqualTo("Promo marzo");
        assertThat(definition.getStatusChanges()).hasSize(1);
        assertThat(definition.getStatusChanges().get(0).getEvent()).isEqualTo(DiscountDefinitionEvent.DEFINITION_CREATED);
        assertThat(definition.getStatusChanges().get(0).getActorPersonId()).isEqualTo(7L);
        assertThat(definition.getStatusChanges().get(0).getFromStatus()).isNull();
    }

    @Test
    void anApprovedProposalKeepsTheProposerAndTheAuthorizationRequest() {
        DiscountDefinition definition = DiscountDefinition.createApprovedDiscountDefinition(DiscountDraftBuilder.cohortPromotion().build(), 5L, 99L, NOW);

        assertThat(definition.getStatus()).isEqualTo(DiscountDefinitionStatus.ACTIVE);
        assertThat(definition.isDirectPublication()).isFalse();
        assertThat(definition.getAuthorizationRequestId()).isEqualTo(99L);
        assertThat(definition.getCreatedById()).isEqualTo(5L);
        assertThat(definition.getStatusChanges().get(0).getActorPersonId()).isEqualTo(5L);
    }

    @Test
    void theDraftValuesAreCopiedIntoTheDefinition() {
        DiscountDefinition definition = DiscountDefinition.publishDiscountDefinition(DiscountDraftBuilder.cohortPromotion()
                .withInstallments(3).withPriority(2).withCap("12000.00", Currency.ARS).forProgram(4L).forCohort(8L)
                .availableFrom(LocalDate.of(2031, 3, 1)).availableUntil(LocalDate.of(2031, 6, 30))
                .withEnrollmentWindow(LocalDate.of(2031, 2, 1), LocalDate.of(2031, 3, 31)).withQuota(10).build(), 1L, NOW);

        assertThat(definition.getDiscountType()).isEqualTo(DiscountDefinitionType.COHORT_PROMOTION);
        assertThat(definition.getInstallmentsTotal()).isEqualTo(3);
        assertThat(definition.getPriority()).isEqualTo(2);
        assertThat(definition.getCapPerInvoice()).isEqualTo(NonNegativeMoney.of(new BigDecimal("12000.00"), Currency.ARS));
        assertThat(definition.getProgramId()).isEqualTo(4L);
        assertThat(definition.getProgramCohortId()).isEqualTo(8L);
        assertThat(definition.getCourseId()).isNull();
        assertThat(definition.getAvailableFrom()).isEqualTo(LocalDate.of(2031, 3, 1));
        assertThat(definition.getAvailableUntil()).isEqualTo(LocalDate.of(2031, 6, 30));
        assertThat(definition.getEnrollmentWindowFrom()).isEqualTo(LocalDate.of(2031, 2, 1));
        assertThat(definition.getEnrollmentWindowTo()).isEqualTo(LocalDate.of(2031, 3, 31));
        assertThat(definition.getQuota()).isEqualTo(10);
    }

    @Test
    void aDefinitionWithoutCapKeepsItNull() {
        assertThat(DiscountDefinition.publishDiscountDefinition(DiscountDraftBuilder.cohortPromotion().build(), 1L, NOW).getCapPerInvoice()).isNull();
    }

    @Test
    void closingRecordsWhoWhenAndWhy() {
        DiscountDefinition definition = DiscountDefinition.publishDiscountDefinition(DiscountDraftBuilder.cohortPromotion().build(), 1L, NOW);
        Instant closeTime = NOW.plusSeconds(3600);

        definition.closeDiscountDefinition("Se agotó la campaña", StatusChangeActor.ofPerson(3L), closeTime);

        assertThat(definition.getStatus()).isEqualTo(DiscountDefinitionStatus.CLOSED);
        assertThat(definition.getClosedById()).isEqualTo(3L);
        assertThat(definition.getClosedAt()).isEqualTo(closeTime);
        assertThat(definition.getClosureReason()).isEqualTo("Se agotó la campaña");
        assertThat(definition.getStatusChanges()).hasSize(2);
        DiscountDefinitionStatusChange closure = definition.getStatusChanges().get(1);
        assertThat(closure.getFromStatus()).isEqualTo(DiscountDefinitionStatus.ACTIVE);
        assertThat(closure.getToStatus()).isEqualTo(DiscountDefinitionStatus.CLOSED);
        assertThat(closure.getEvent()).isEqualTo(DiscountDefinitionEvent.DEFINITION_CLOSED);
        assertThat(closure.getReason()).isEqualTo("Se agotó la campaña");
        assertThat(closure.getChangedAt()).isEqualTo(closeTime);
    }

    @Test
    void closingTwiceIsRejectedWithoutChangingAnything() {
        DiscountDefinition definition = DiscountDefinition.publishDiscountDefinition(DiscountDraftBuilder.cohortPromotion().build(), 1L, NOW);
        definition.closeDiscountDefinition("uno", StatusChangeActor.ofPerson(3L), NOW);

        assertThatThrownBy(() -> definition.closeDiscountDefinition("dos", StatusChangeActor.ofPerson(4L), NOW.plusSeconds(10)))
                .isInstanceOf(IllegalDiscountDefinitionTransitionException.class);
        assertThat(definition.getClosureReason()).isEqualTo("uno");
        assertThat(definition.getStatusChanges()).hasSize(2);
    }

    @Test
    void theStatusChangesAreAReadOnlyCopy() {
        DiscountDefinition definition = DiscountDefinition.publishDiscountDefinition(DiscountDraftBuilder.cohortPromotion().build(), 1L, NOW);

        assertThatThrownBy(() -> definition.getStatusChanges().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void theQuotaCostIsTheSavingPerInstallmentTimesInstallmentsTimesPeople() {
        DiscountDefinition definition = DiscountDefinition.publishDiscountDefinition(
                DiscountDraftBuilder.cohortPromotion().withPercent("10").withInstallments(3).withQuota(2).build(), 1L, NOW);

        assertThat(definition.estimateQuotaCost(PRICE)).contains(NonNegativeMoney.of(new BigDecimal("60000.00"), Currency.ARS));
    }

    @Test
    void theQuotaCostUsesTheCapWhenTheSavingExceedsIt() {
        DiscountDefinition definition = DiscountDefinition.publishDiscountDefinition(
                DiscountDraftBuilder.cohortPromotion().withPercent("20").withCap("12000.00", Currency.ARS).withInstallments(2).withQuota(5).build(), 1L, NOW);

        assertThat(definition.estimateQuotaCost(PRICE)).contains(NonNegativeMoney.of(new BigDecimal("120000.00"), Currency.ARS));
    }

    @Test
    void theQuotaCostOfAFixedAmountNeverExceedsThePrice() {
        DiscountDefinition definition = DiscountDefinition.publishDiscountDefinition(
                DiscountDraftBuilder.cohortPromotion().withFixedAmount("150000.00", Currency.ARS).withInstallments(1).withQuota(2).build(), 1L, NOW);

        assertThat(definition.estimateQuotaCost(PRICE)).contains(NonNegativeMoney.of(new BigDecimal("200000.00"), Currency.ARS));
    }

    @Test
    @Tag("D-326")
    void aSingleChargePromotionCostsOneUsePerPerson() {
        DiscountDefinition definition = DiscountDefinition.publishDiscountDefinition(DiscountDraftBuilder.cohortPromotion().withPercent("20")
                .forInvoiceType(com.red_escuela.backend.billing.invoice.domain.InvoiceType.ENROLLMENT_FEE).withoutInstallments().withQuota(50).build(), 1L, NOW);

        assertThat(definition.estimateQuotaCost(NonNegativeMoney.of(new BigDecimal("50000.00"), Currency.ARS)))
                .contains(NonNegativeMoney.of(new BigDecimal("500000.00"), Currency.ARS));
    }

    @Test
    void aSingleChargePromotionWithoutQuotaIsUnlimited() {
        DiscountDefinition definition = DiscountDefinition.publishDiscountDefinition(DiscountDraftBuilder.cohortPromotion()
                .forInvoiceType(com.red_escuela.backend.billing.invoice.domain.InvoiceType.ENROLLMENT_FEE).withoutInstallments().build(), 1L, NOW);

        assertThat(definition.estimateQuotaCost(PRICE)).isEmpty();
    }

    @Test
    void withoutQuotaTheCostIsUnlimited() {
        DiscountDefinition definition = DiscountDefinition.publishDiscountDefinition(DiscountDraftBuilder.cohortPromotion().withInstallments(3).build(), 1L, NOW);

        assertThat(definition.estimateQuotaCost(PRICE)).isEmpty();
    }

    @Test
    void withoutInstallmentCountTheCostIsUnlimited() {
        DiscountDefinition definition = DiscountDefinition.publishDiscountDefinition(DiscountDraftBuilder.cohortPromotion().withoutInstallments().withQuota(2).build(), 1L, NOW);

        assertThat(definition.estimateQuotaCost(PRICE)).isEqualTo(Optional.empty());
    }

    @Test
    void aScholarshipWithoutCalculationHasNoEstimatedCost() {
        DiscountDefinition definition = DiscountDefinition.publishDiscountDefinition(DiscountDraftBuilder.scholarship().build(), 1L, NOW);

        assertThat(definition.estimateQuotaCost(PRICE)).isEmpty();
    }

    @Test
    void acquisitionIsOnlyPossibleInsideTheAvailabilityPeriod() {
        DiscountDefinition definition = DiscountDefinition.publishDiscountDefinition(DiscountDraftBuilder.cohortPromotion()
                .availableFrom(LocalDate.of(2031, 3, 1)).availableUntil(LocalDate.of(2031, 6, 30)).build(), 1L, NOW);

        assertThat(definition.isAvailableForAcquisitionOn(LocalDate.of(2031, 2, 28))).isFalse();
        assertThat(definition.isAvailableForAcquisitionOn(LocalDate.of(2031, 3, 1))).isTrue();
        assertThat(definition.isAvailableForAcquisitionOn(LocalDate.of(2031, 6, 30))).isTrue();
        assertThat(definition.isAvailableForAcquisitionOn(LocalDate.of(2031, 7, 1))).isFalse();
    }

    @Test
    void acquisitionWithoutAnEndDateNeverExpires() {
        DiscountDefinition definition = DiscountDefinition.publishDiscountDefinition(DiscountDraftBuilder.cohortPromotion().availableFrom(LocalDate.of(2031, 3, 1)).build(), 1L, NOW);

        assertThat(definition.isAvailableForAcquisitionOn(LocalDate.of(2040, 1, 1))).isTrue();
    }

    @Test
    void acquisitionRespectsTheEnrollmentWindow() {
        DiscountDefinition definition = DiscountDefinition.publishDiscountDefinition(DiscountDraftBuilder.cohortPromotion()
                .withEnrollmentWindow(LocalDate.of(2031, 3, 10), LocalDate.of(2031, 3, 20)).build(), 1L, NOW);

        assertThat(definition.isAvailableForAcquisitionOn(LocalDate.of(2031, 3, 9))).isFalse();
        assertThat(definition.isAvailableForAcquisitionOn(LocalDate.of(2031, 3, 10))).isTrue();
        assertThat(definition.isAvailableForAcquisitionOn(LocalDate.of(2031, 3, 20))).isTrue();
        assertThat(definition.isAvailableForAcquisitionOn(LocalDate.of(2031, 3, 21))).isFalse();
    }

    @Test
    void availabilityIsUpcomingBeforeTheStartAvailableOnBothEdgesAndExpiredAfterTheEnd() {
        DiscountDefinition definition = DiscountDefinition.publishDiscountDefinition(DiscountDraftBuilder.cohortPromotion()
                .availableFrom(LocalDate.of(2031, 3, 1)).availableUntil(LocalDate.of(2031, 6, 30)).build(), 1L, NOW);

        assertThat(definition.availabilityOn(LocalDate.of(2031, 2, 28))).isEqualTo(DiscountDefinitionAvailability.UPCOMING);
        assertThat(definition.availabilityOn(LocalDate.of(2031, 3, 1))).isEqualTo(DiscountDefinitionAvailability.AVAILABLE);
        assertThat(definition.availabilityOn(LocalDate.of(2031, 6, 30))).isEqualTo(DiscountDefinitionAvailability.AVAILABLE);
        assertThat(definition.availabilityOn(LocalDate.of(2031, 7, 1))).isEqualTo(DiscountDefinitionAvailability.EXPIRED);
    }

    @Test
    void availabilityWithoutAnEndDateNeverExpires() {
        DiscountDefinition definition = DiscountDefinition.publishDiscountDefinition(DiscountDraftBuilder.cohortPromotion().availableFrom(LocalDate.of(2031, 3, 1)).build(), 1L, NOW);

        assertThat(definition.availabilityOn(LocalDate.of(2060, 1, 1))).isEqualTo(DiscountDefinitionAvailability.AVAILABLE);
    }

    @Test
    void theCostIsPricedOnTheFeeOfTheInvoiceTypeTheDefinitionAppliesTo() {
        for (InvoiceType invoiceType : InvoiceType.values()) {
            if (invoiceType == InvoiceType.LATE_FEE) {
                continue;
            }
            DiscountDefinition definition = DiscountDefinition.publishDiscountDefinition(DiscountDraftBuilder.cohortPromotion().forInvoiceType(invoiceType).withoutInstallments().build(), 1L, NOW);

            assertThat(definition.findPricedFeeType().map(Enum::name)).isEqualTo(invoiceType == InvoiceType.ANNUAL_UPFRONT ? Optional.empty() : Optional.of(invoiceType.name()));
        }
    }

    @Test
    void thePricedOfferingIsTheProgramOrTheCourseOfTheScopeAndNothingWithoutScope() {
        DiscountDefinition forProgram = DiscountDefinition.publishDiscountDefinition(DiscountDraftBuilder.cohortPromotion().forProgram(4L).build(), 1L, NOW);
        DiscountDefinition forCourse = DiscountDefinition.publishDiscountDefinition(DiscountDraftBuilder.cohortPromotion().forCourse(9L).build(), 1L, NOW);
        DiscountDefinition withoutScope = DiscountDefinition.publishDiscountDefinition(DiscountDraftBuilder.annualUpfront().build(), 1L, NOW);

        assertThat(forProgram.findPricedOffering()).contains(OfferingReference.ofProgram(4L));
        assertThat(forCourse.findPricedOffering()).contains(OfferingReference.ofCourse(9L));
        assertThat(withoutScope.findPricedOffering()).isEmpty();
    }

    @Test
    void aClosedDefinitionCannotBeAcquired() {
        DiscountDefinition definition = DiscountDefinition.publishDiscountDefinition(DiscountDraftBuilder.cohortPromotion().build(), 1L, NOW);
        definition.closeDiscountDefinition("cierre", StatusChangeActor.ofPerson(1L), NOW);

        assertThat(definition.isAvailableForAcquisitionOn(LocalDate.of(2031, 3, 15))).isFalse();
    }
}
