package com.red_escuela.backend.evaluation.domain;

import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GradeGridConditionTest {

    @Test
    void statusesThatAreAlreadyDecidedIgnoreAbsences() {
        assertThat(GradeGridCondition.of(EnrollmentStatus.REGULAR, true)).isEqualTo(GradeGridCondition.REGULAR);
        assertThat(GradeGridCondition.of(EnrollmentStatus.APPROVED, true)).isEqualTo(GradeGridCondition.APPROVED);
        assertThat(GradeGridCondition.of(EnrollmentStatus.FAILED, true)).isEqualTo(GradeGridCondition.FAILED);
    }

    @Test
    void enrolledAndInProgressAreFreeOnlyWhenOverTheAbsenceLimit() {
        assertThat(GradeGridCondition.of(EnrollmentStatus.ENROLLED, true)).isEqualTo(GradeGridCondition.FREE);
        assertThat(GradeGridCondition.of(EnrollmentStatus.IN_PROGRESS, true)).isEqualTo(GradeGridCondition.FREE);
        assertThat(GradeGridCondition.of(EnrollmentStatus.ENROLLED, false)).isEqualTo(GradeGridCondition.IN_PROGRESS);
        assertThat(GradeGridCondition.of(EnrollmentStatus.IN_PROGRESS, false)).isEqualTo(GradeGridCondition.IN_PROGRESS);
    }

    @Test
    void statusesOutsideTheSectionRosterHaveNoCondition() {
        assertThatThrownBy(() -> GradeGridCondition.of(EnrollmentStatus.WITHDRAWN, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GradeGridCondition.of(EnrollmentStatus.WAITLISTED, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GradeGridCondition.of(EnrollmentStatus.WAITLIST_EXPIRED, false))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
