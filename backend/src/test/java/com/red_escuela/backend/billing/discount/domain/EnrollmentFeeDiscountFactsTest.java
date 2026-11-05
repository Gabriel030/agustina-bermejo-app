package com.red_escuela.backend.billing.discount.domain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-416")
class EnrollmentFeeDiscountFactsTest {

    @Test
    void reissueLosesTheDiscountOnlyWhenTheFeeCarriesOneAndAnotherLiveFeeExistsInTheSameYear() {
        assertThat(new EnrollmentFeeDiscountFacts(true, true).reissueWouldLoseDiscount()).isTrue();
        assertThat(new EnrollmentFeeDiscountFacts(true, false).reissueWouldLoseDiscount()).isFalse();
        assertThat(new EnrollmentFeeDiscountFacts(false, true).reissueWouldLoseDiscount()).isFalse();
        assertThat(new EnrollmentFeeDiscountFacts(false, false).reissueWouldLoseDiscount()).isFalse();
    }
}
