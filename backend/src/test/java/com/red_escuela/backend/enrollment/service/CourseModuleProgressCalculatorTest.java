package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.shared.domain.Percentage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CourseModuleProgressCalculatorTest {

    @Test
    void courseWithoutActiveModulesHasNoProgress() {
        assertThat(CourseModuleProgressCalculator.calculateApprovedModulesPercentage(Set.of(), List.of(1L))).isEmpty();
    }

    @Test
    void noApprovedModulesIsZeroPercent() {
        Optional<Percentage> progress = CourseModuleProgressCalculator.calculateApprovedModulesPercentage(Set.of(1L, 2L), List.of());
        assertThat(progress).hasValueSatisfying(value -> assertThat(value.points()).isEqualByComparingTo("0"));
    }

    @Test
    void oneOfFourApprovedIsTwentyFivePercent() {
        Optional<Percentage> progress = CourseModuleProgressCalculator.calculateApprovedModulesPercentage(Set.of(1L, 2L, 3L, 4L), List.of(2L));
        assertThat(progress).hasValueSatisfying(value -> assertThat(value.points()).isEqualByComparingTo("25"));
    }

    @Test
    void allApprovedIsOneHundredPercent() {
        Optional<Percentage> progress = CourseModuleProgressCalculator.calculateApprovedModulesPercentage(Set.of(1L, 2L), List.of(1L, 2L));
        assertThat(progress).hasValueSatisfying(value -> assertThat(value.points()).isEqualByComparingTo("100"));
    }

    @Test
    void approvedModuleNoLongerActiveDoesNotCount() {
        Optional<Percentage> progress = CourseModuleProgressCalculator.calculateApprovedModulesPercentage(Set.of(1L, 2L), List.of(1L, 99L));
        assertThat(progress).hasValueSatisfying(value -> assertThat(value.points()).isEqualByComparingTo("50"));
    }

    @Test
    void repeatedApprovalsOfSameModuleCountOnce() {
        Optional<Percentage> progress = CourseModuleProgressCalculator.calculateApprovedModulesPercentage(Set.of(1L, 2L), List.of(1L, 1L));
        assertThat(progress).hasValueSatisfying(value -> assertThat(value.points()).isEqualByComparingTo("50"));
    }
}
