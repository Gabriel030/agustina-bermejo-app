package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.api.WithdrawalCause;
import com.red_escuela.backend.enrollment.exception.WithdrawalDateOutOfAllowedRangeException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-249")
class EnrollmentWithdrawalDateResolverTest {

    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final LocalDateTime ENROLLED_AT = LocalDateTime.of(2027, 3, 1, 10, 0);

    private EnrollmentWithdrawalDateResolver resolverAt(LocalDateTime localNow) {
        return new EnrollmentWithdrawalDateResolver(Clock.fixed(localNow.atZone(ZONE).toInstant(), ZONE));
    }

    @Test
    void withoutInformedDateTheEffectiveDateIsTheLocalDateOfTheClock() {
        EnrollmentWithdrawalDateResolver resolver = resolverAt(LocalDateTime.of(2027, 6, 1, 0, 30));

        assertThat(resolver.resolveEffectiveDate(new EnrollmentWithdrawal(null, null, WithdrawalCause.VOLUNTARY), ENROLLED_AT)).isEqualTo(LocalDate.of(2027, 6, 1));
    }

    @Test
    void informedDateWithinTheRangeIsTheEffectiveDate() {
        EnrollmentWithdrawalDateResolver resolver = resolverAt(LocalDateTime.of(2027, 5, 3, 9, 0));

        assertThat(resolver.resolveEffectiveDate(EnrollmentWithdrawal.voluntaryRegisteredBy(LocalDate.of(2027, 4, 28), 5L), ENROLLED_AT))
                .isEqualTo(LocalDate.of(2027, 4, 28));
    }

    @Test
    void informedDateOutOfTheRangeIsRejectedWithItsViolationCode() {
        EnrollmentWithdrawalDateResolver resolver = resolverAt(LocalDateTime.of(2027, 5, 3, 9, 0));

        assertThatThrownBy(() -> resolver.resolveEffectiveDate(EnrollmentWithdrawal.voluntaryRegisteredBy(LocalDate.of(2027, 3, 31), 5L), ENROLLED_AT))
                .isInstanceOfSatisfying(WithdrawalDateOutOfAllowedRangeException.class,
                        exception -> assertThat(exception.violations()).extracting("code").containsExactly("WITHDRAWAL_DATE_OUT_OF_ALLOWED_RANGE"));
    }

    @Test
    void unknownEnrollmentDateDoesNotBlockTheWithdrawal() {
        EnrollmentWithdrawalDateResolver resolver = resolverAt(LocalDateTime.of(2027, 5, 3, 9, 0));

        assertThat(resolver.resolveEffectiveDate(new EnrollmentWithdrawal(null, null, WithdrawalCause.VOLUNTARY), null)).isEqualTo(LocalDate.of(2027, 5, 3));
    }

    @Test
    void completionMomentKeepsTheTimeOfTheClockOnTheEffectiveDate() {
        EnrollmentWithdrawalDateResolver resolver = resolverAt(LocalDateTime.of(2027, 5, 3, 9, 15, 30));

        assertThat(resolver.completionMomentOf(LocalDate.of(2027, 4, 28))).isEqualTo(LocalDateTime.of(2027, 4, 28, 9, 15, 30));
    }

    @Test
    void lateEveningInTheBusinessZoneIsStillThePreviousMonthEvenIfUtcAlreadyChangedMonth() {
        Instant lateEveningLocal = LocalDateTime.of(2027, 5, 31, 22, 0).atZone(ZONE).toInstant();
        EnrollmentWithdrawalDateResolver resolver = new EnrollmentWithdrawalDateResolver(Clock.fixed(lateEveningLocal, ZONE));

        assertThat(lateEveningLocal.atZone(ZoneId.of("UTC")).toLocalDate()).isEqualTo(LocalDate.of(2027, 6, 1));
        assertThat(resolver.resolveEffectiveDate(new EnrollmentWithdrawal(null, null, WithdrawalCause.VOLUNTARY), ENROLLED_AT)).isEqualTo(LocalDate.of(2027, 5, 31));
    }
}
