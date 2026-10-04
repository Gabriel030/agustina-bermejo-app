package com.red_escuela.backend.shared.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GradeTest {

    @ParameterizedTest
    @ValueSource(strings = {"1", "7", "7.5", "7.25", "10", "10.00"})
    void gradesBetweenOneAndTenWithUpToTwoDecimalsAreAccepted(String raw) {
        assertThat(Grade.of(new BigDecimal(raw)).value()).isEqualByComparingTo(raw);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.99", "0", "-3", "10.01", "10.20", "11", "70"})
    void gradesOutsideTheScaleAreRejected(String raw) {
        assertThatThrownBy(() -> Grade.of(new BigDecimal(raw))).isInstanceOf(InvalidGradeException.class);
    }

    @Test
    void aLoadedGradeWithMoreThanTwoDecimalsIsRejectedInsteadOfRounded() {
        assertThatThrownBy(() -> Grade.of(new BigDecimal("7.555"))).isInstanceOf(InvalidGradeException.class);
    }

    @Test
    void trailingZerosBeyondTwoDecimalsAreAccepted() {
        assertThat(Grade.of(new BigDecimal("7.500")).value()).isEqualTo(new BigDecimal("7.50"));
    }

    @Test
    void valueIsAlwaysKeptWithTwoDecimals() {
        assertThat(Grade.of(new BigDecimal("7")).value()).isEqualTo(new BigDecimal("7.00"));
    }

    @Test
    void averageOfRoundsHalfUpToTwoDecimals() {
        assertThat(Grade.averageOf(new BigDecimal("7.555"))).isEqualTo(Grade.of(new BigDecimal("7.56")));
    }

    @Test
    void averageOfAListRoundsHalfUpAndNeverExceedsTen() {
        Grade average = Grade.average(List.of(Grade.of(new BigDecimal("7")), Grade.of(new BigDecimal("8")),
                Grade.of(new BigDecimal("8")))).orElseThrow();
        assertThat(average.value()).isEqualTo(new BigDecimal("7.67"));
        assertThat(Grade.average(List.of(Grade.of(BigDecimal.TEN), Grade.of(BigDecimal.TEN))).orElseThrow().value())
                .isEqualTo(new BigDecimal("10.00"));
    }

    @Test
    void averageOfAnEmptyListIsEmpty() {
        assertThat(Grade.average(List.of())).isEmpty();
    }

    @Test
    void meetsIsInclusiveOfTheThreshold() {
        Grade six = Grade.of(new BigDecimal("6.00"));
        assertThat(Grade.of(new BigDecimal("6")).meets(six)).isTrue();
        assertThat(Grade.of(new BigDecimal("5.99")).meets(six)).isFalse();
        assertThat(Grade.of(new BigDecimal("6.01")).meets(six)).isTrue();
    }
}
