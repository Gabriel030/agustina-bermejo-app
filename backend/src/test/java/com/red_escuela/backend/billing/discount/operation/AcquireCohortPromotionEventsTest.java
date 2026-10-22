package com.red_escuela.backend.billing.discount.operation;

import com.red_escuela.backend.billing.discount.DiscountDraftBuilder;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinition;
import com.red_escuela.backend.billing.events.DiscountQuotaExhaustedEvent;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-240")
class AcquireCohortPromotionEventsTest {
    private static final Instant NOW = Instant.parse("2027-02-01T10:00:00Z");
    private final AcquireCohortPromotionOperation operation = new AcquireCohortPromotionOperation(null, null, null, null, null, null, null, null);

    private DiscountDefinition definitionWithQuota(Long id, Integer quota) {
        DiscountDefinition definition = DiscountDefinition.publishDiscountDefinition(DiscountDraftBuilder.cohortPromotion().named("Cohorte").withQuota(quota).build(), 7L, NOW);
        ReflectionTestUtils.setField(definition, "id", id);
        return definition;
    }

    private AcquireCohortPromotionCommand command() {
        return new AcquireCohortPromotionCommand(com.red_escuela.backend.billing.discount.domain.EnrollmentReference.ofProgram(1L),
                com.red_escuela.backend.shared.pricing.OfferingReference.ofProgram(1L), null, LocalDate.of(2027, 2, 1), 7L);
    }

    @Test
    void takingTheLastPlacePublishesTheExhaustionOnceWithTheDefinitionAndItsQuota() {
        DiscountDefinition definition = definitionWithQuota(5L, 2);

        List<?> events = operation.domainEventsOf(command(), Optional.of(new AcquiredCohortPromotion(null, definition, true)));

        assertThat(events).singleElement().isInstanceOfSatisfying(DiscountQuotaExhaustedEvent.class, event -> {
            assertThat(event.discountDefinitionId()).isEqualTo(5L);
            assertThat(event.discountName()).isEqualTo("Cohorte");
            assertThat(event.quota()).isEqualTo(2);
        });
    }

    @Test
    void anAcquisitionThatDoesNotFillTheQuotaPublishesNothing() {
        assertThat(operation.domainEventsOf(command(), Optional.of(new AcquiredCohortPromotion(null, definitionWithQuota(5L, 2), false)))).isEmpty();
    }

    @Test
    void anAcquisitionThatFoundNoPromotionPublishesNothing() {
        assertThat(operation.domainEventsOf(command(), Optional.empty())).isEmpty();
    }
}
