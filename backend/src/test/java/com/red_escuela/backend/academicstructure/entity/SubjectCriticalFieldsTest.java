package com.red_escuela.backend.academicstructure.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SubjectCriticalFieldsTest {

    private Subject subject(SubjectStatus status) {
        return Subject.builder()
                .id(7L)
                .status(status)
                .gradeMode(GradeMode.NUMERIC)
                .maxFinalExamAttempts(3)
                .weeklyHours(4)
                .weeklyHoursLab(2)
                .build();
    }

    @Test
    void changingGradeModeOfApprovedSubjectWithPreviousSectionsIsBlocked() {
        var subject = subject(SubjectStatus.ACTIVE);

        assertThat(subject.isCriticalFieldsChangeBlocked(true, GradeMode.PASS_FAIL, null, null, null)).isTrue();
    }

    @Test
    void changingAttemptsOfApprovedSubjectWithPreviousSectionsIsBlocked() {
        assertThat(subject(SubjectStatus.ACTIVE).isCriticalFieldsChangeBlocked(true, null, 4, null, null)).isTrue();
    }

    @Test
    void changingWeeklyHoursOfApprovedSubjectWithPreviousSectionsIsBlocked() {
        assertThat(subject(SubjectStatus.ACTIVE).isCriticalFieldsChangeBlocked(true, null, null, 5, null)).isTrue();
    }

    @Test
    void changingLabHoursOfApprovedSubjectWithPreviousSectionsIsBlocked() {
        assertThat(subject(SubjectStatus.ACTIVE).isCriticalFieldsChangeBlocked(true, null, null, null, 3)).isTrue();
    }

    @Test
    void resendingTheSameCriticalValuesIsAllowed() {
        var subject = subject(SubjectStatus.ACTIVE);

        assertThat(subject.isCriticalFieldsChangeBlocked(true, subject.getGradeMode(), 3, 4, 2)).isFalse();
    }

    @Test
    void approvedSubjectWithoutPreviousSectionsCanChangeCriticalFields() {
        assertThat(subject(SubjectStatus.ACTIVE).isCriticalFieldsChangeBlocked(false, null, 4, 5, 3)).isFalse();
    }

    @Test
    void draftSubjectWithSectionsCanChangeCriticalFields() {
        assertThat(subject(SubjectStatus.DRAFT).isCriticalFieldsChangeBlocked(true, null, 4, 5, 3)).isFalse();
    }
}
