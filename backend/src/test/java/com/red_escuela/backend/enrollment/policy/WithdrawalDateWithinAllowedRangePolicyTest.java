package com.red_escuela.backend.enrollment.policy;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-249")
@Tag("D-141")
class WithdrawalDateWithinAllowedRangePolicyTest {

    private final WithdrawalDateWithinAllowedRangePolicy policy = new WithdrawalDateWithinAllowedRangePolicy();

    @ParameterizedTest(name = "today {0} enrolled {1} informed {2} allowed {3}")
    @CsvSource({
            "2027-05-15, 2027-03-01, 2027-05-15, true",
            "2027-05-15, 2027-03-01, 2027-04-01, true",
            "2027-05-15, 2027-03-01, 2027-03-31, false",
            "2027-05-15, 2027-03-01, 2027-05-16, false",
            "2027-05-15, 2027-03-01, 2027-04-30, true",
            "2027-05-01, 2027-03-01, 2027-04-01, true",
            "2027-05-01, 2027-03-01, 2027-03-31, false",
            "2028-01-15, 2027-03-01, 2027-12-01, true",
            "2028-01-15, 2027-03-01, 2027-11-30, false",
            "2028-01-01, 2027-03-01, 2028-01-01, true",
            "2027-03-31, 2027-03-01, 2027-02-28, false",
            "2027-03-31, 2027-03-01, 2027-03-01, true",
            "2027-05-15, 2027-04-10, 2027-04-09, false",
            "2027-05-15, 2027-04-10, 2027-04-10, true",
            "2027-03-01, 2027-03-01, 2027-02-01, false",
            "2028-02-29, 2027-03-01, 2027-12-31, false",
            "2028-02-29, 2027-03-01, 2028-01-31, true",
            "2028-02-29, 2027-03-01, 2028-01-01, true",
            "2027-12-31, 2027-03-01, 2027-11-01, true",
            "2027-12-31, 2027-03-01, 2027-10-31, false"
    })
    void allowsOnlyDatesFromFirstDayOfPreviousMonthUpToTodayAndNotBeforeEnrollment(LocalDate today, LocalDate enrolledOn, LocalDate informed, boolean allowed) {
        assertThat(policy.evaluateRule(new WithdrawalDateContext(informed, today, Optional.of(enrolledOn))).allowed()).isEqualTo(allowed);
    }

    @ParameterizedTest
    @CsvSource({"2027-05-15, 2027-05-15, true", "2027-05-15, 2027-04-01, true", "2027-05-15, 2027-03-31, false", "2027-05-15, 2027-05-16, false"})
    void ignoresTheEnrollmentLowerBoundWhenTheEnrollmentDateIsUnknown(LocalDate today, LocalDate informed, boolean allowed) {
        assertThat(policy.evaluateRule(new WithdrawalDateContext(informed, today, Optional.empty())).allowed()).isEqualTo(allowed);
    }

    @org.junit.jupiter.api.Test
    void deniesWithTheDocumentedViolationCode() {
        var verdict = policy.evaluateRule(new WithdrawalDateContext(LocalDate.of(2027, 3, 31), LocalDate.of(2027, 5, 15), Optional.empty()));

        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.code()).isEqualTo("WITHDRAWAL_DATE_OUT_OF_ALLOWED_RANGE");
    }
}
