package com.red_escuela.backend.billing.reporting.domain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-146")
class OverdueAgeBucketTest {

    @ParameterizedTest
    @CsvSource({"1,FROM_1_TO_30", "10,FROM_1_TO_30", "30,FROM_1_TO_30", "31,FROM_31_TO_60", "45,FROM_31_TO_60", "60,FROM_31_TO_60", "61,FROM_61_TO_90", "70,FROM_61_TO_90",
            "90,FROM_61_TO_90", "91,OVER_90", "120,OVER_90", "3650,OVER_90"})
    void everyDayOfAgeBelongsToExactlyOneBucketWithInclusiveUpperBorders(long days, OverdueAgeBucket expected) {
        assertThat(OverdueAgeBucket.forDaysOverdue(days)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"0", "-1", "-30"})
    void anInvoiceNotYetPastItsDueDateHasNoAge(long days) {
        assertThatThrownBy(() -> OverdueAgeBucket.forDaysOverdue(days)).isInstanceOf(IllegalArgumentException.class);
    }
}
