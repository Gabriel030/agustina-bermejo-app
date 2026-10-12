package com.red_escuela.backend.shared.domain;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tag;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-099")
@Tag("D-174")
class PercentageProperties {

    @Provide
    Arbitrary<BigDecimal> pointsInRange() {
        return Arbitraries.bigDecimals().between(BigDecimal.ZERO, new BigDecimal("100.00")).ofScale(2);
    }

    @Provide
    Arbitrary<BigDecimal> pointsAboveRange() {
        return Arbitraries.bigDecimals().between(new BigDecimal("100.01"), new BigDecimal("1000000.00")).ofScale(2);
    }

    @Provide
    Arbitrary<BigDecimal> pointsBelowRange() {
        return Arbitraries.bigDecimals().between(new BigDecimal("-1000000.00"), new BigDecimal("-0.01")).ofScale(2);
    }

    @Property(tries = PropertySettings.TRIES, seed = PropertySettings.SEED)
    void pointsInZeroToOneHundredAreAcceptedAndRoundTrip(@ForAll("pointsInRange") BigDecimal points) {
        Percentage percentage = Percentage.ofPoints(points);

        assertThat(percentage.points()).isEqualByComparingTo(points);
        assertThat(percentage.fraction()).isBetween(BigDecimal.ZERO, BigDecimal.ONE);
    }

    @Property(tries = PropertySettings.TRIES, seed = PropertySettings.SEED)
    void pointsAboveOneHundredAreRejected(@ForAll("pointsAboveRange") BigDecimal points) {
        assertThatThrownBy(() -> Percentage.ofPoints(points)).isInstanceOf(InvalidPercentageException.class);
    }

    @Property(tries = PropertySettings.TRIES, seed = PropertySettings.SEED)
    void negativePointsAreRejected(@ForAll("pointsBelowRange") BigDecimal points) {
        assertThatThrownBy(() -> Percentage.ofPoints(points)).isInstanceOf(InvalidPercentageException.class);
    }

    @Property(tries = PropertySettings.TRIES, seed = PropertySettings.SEED)
    void exceedsAgreesWithTheOrderOfThePoints(@ForAll("pointsInRange") BigDecimal first, @ForAll("pointsInRange") BigDecimal second) {
        assertThat(Percentage.ofPoints(first).exceeds(Percentage.ofPoints(second))).isEqualTo(first.compareTo(second) > 0);
    }

    @Property(tries = PropertySettings.TRIES, seed = PropertySettings.SEED)
    void ratioOfAPartInsideTheTotalIsAValidPercentage(@ForAll @net.jqwik.api.constraints.LongRange(min = 0, max = 100000) long part,
                                                      @ForAll @net.jqwik.api.constraints.LongRange(min = 1, max = 100000) long extra) {
        long total = part + extra;

        Percentage ratio = Percentage.ratio(part, total).orElseThrow();

        assertThat(ratio.fraction()).isBetween(BigDecimal.ZERO, BigDecimal.ONE);
    }
}
