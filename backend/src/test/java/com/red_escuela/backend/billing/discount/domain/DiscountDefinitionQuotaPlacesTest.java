package com.red_escuela.backend.billing.discount.domain;

import com.red_escuela.backend.billing.discount.DiscountDraftBuilder;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-240")
class DiscountDefinitionQuotaPlacesTest {
    private static final Instant NOW = Instant.parse("2031-03-10T10:00:00Z");

    private DiscountDefinition withQuota(Integer quota) {
        return DiscountDefinition.publishDiscountDefinition(DiscountDraftBuilder.cohortPromotion().withQuota(quota).build(), 7L, NOW);
    }

    @Test
    void aDefinitionHasAPlaceLeftWhileThePlacesUsedAreBelowTheQuota() {
        DiscountDefinition definition = withQuota(2);

        assertThat(definition.hasPlaceLeft(0)).isTrue();
        assertThat(definition.hasPlaceLeft(1)).isTrue();
        assertThat(definition.hasPlaceLeft(2)).isFalse();
        assertThat(definition.hasPlaceLeft(3)).isFalse();
    }

    @Test
    void aDefinitionWithoutQuotaAlwaysHasAPlaceLeftAndNeverTakesTheLastOne() {
        DiscountDefinition definition = withQuota(null);

        assertThat(definition.hasPlaceLeft(1_000_000)).isTrue();
        assertThat(definition.takesLastPlaceAfter(0)).isFalse();
    }

    @Test
    void onlyTheAcquisitionThatFillsTheQuotaTakesTheLastPlace() {
        DiscountDefinition definition = withQuota(3);

        assertThat(definition.takesLastPlaceAfter(1)).isFalse();
        assertThat(definition.takesLastPlaceAfter(2)).isTrue();
        assertThat(definition.takesLastPlaceAfter(3)).isFalse();
    }

    @Test
    void onceTheExhaustionWasNotifiedTheLastPlaceIsNotReportedAgain() {
        DiscountDefinition definition = withQuota(3);

        definition.markQuotaExhaustionNotified(NOW);

        assertThat(definition.getQuotaExhaustedNotifiedAt()).isEqualTo(NOW);
        assertThat(definition.takesLastPlaceAfter(2)).isFalse();
    }
}
