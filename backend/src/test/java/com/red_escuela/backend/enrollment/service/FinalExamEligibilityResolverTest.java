package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.api.SectionAbsenceLimitPort;
import com.red_escuela.backend.enrollment.dto.FinalExamEligibilityResponse;
import com.red_escuela.backend.enrollment.dto.FinalExamIneligibilityReason;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinalExamEligibilityResolverTest {

    private final SectionAbsenceLimitPort reader = mock(SectionAbsenceLimitPort.class);
    private final FinalExamEligibilityResolver resolver = new FinalExamEligibilityResolver(reader);

    @Test
    void regularCourseworkWithinTheAbsenceLimitIsAllowed() {
        SubjectSectionEnrollment enrollment = enrollment(1L, EnrollmentStatus.REGULAR);
        when(reader.exceedsAbsenceLimit(1L)).thenReturn(standing(false));

        assertThat(resolver.resolve(enrollment)).isEqualTo(FinalExamEligibilityResponse.allowed());
    }

    @Test
    void regularCourseworkOverTheAbsenceLimitIsBlockedByAbsences() {
        SubjectSectionEnrollment enrollment = enrollment(1L, EnrollmentStatus.REGULAR);
        when(reader.exceedsAbsenceLimit(1L)).thenReturn(standing(true));

        FinalExamEligibilityResponse response = resolver.resolve(enrollment);

        assertThat(response.eligible()).isFalse();
        assertThat(response.reason()).isEqualTo(FinalExamIneligibilityReason.FREE_BY_ABSENCES);
    }

    @Test
    void courseworkNotRegularIsBlockedWithoutReadingAttendance() {
        SubjectSectionEnrollment enrollment = enrollment(2L, EnrollmentStatus.IN_PROGRESS);

        FinalExamEligibilityResponse response = resolver.resolve(enrollment);

        assertThat(response.eligible()).isFalse();
        assertThat(response.reason()).isEqualTo(FinalExamIneligibilityReason.COURSEWORK_NOT_REGULAR);
        verify(reader, never()).exceedsAbsenceLimit(2L);
    }

    @Test
    void eachEnrollmentIsDecidedOnItsOwnStanding() {
        SubjectSectionEnrollment fine = enrollment(1L, EnrollmentStatus.REGULAR);
        SubjectSectionEnrollment free = enrollment(2L, EnrollmentStatus.REGULAR);
        when(reader.exceedsAbsenceLimit(1L)).thenReturn(standing(false));
        when(reader.exceedsAbsenceLimit(2L)).thenReturn(standing(true));

        assertThat(resolver.resolve(fine).eligible()).isTrue();
        assertThat(resolver.resolve(free).eligible()).isFalse();
    }

    private SubjectSectionEnrollment enrollment(Long id, EnrollmentStatus status) {
        SubjectSectionEnrollment enrollment = mock(SubjectSectionEnrollment.class);
        when(enrollment.getId()).thenReturn(id);
        when(enrollment.getStatus()).thenReturn(status);
        return enrollment;
    }

    private boolean standing(boolean exceeds) {
        return exceeds;
    }
}
