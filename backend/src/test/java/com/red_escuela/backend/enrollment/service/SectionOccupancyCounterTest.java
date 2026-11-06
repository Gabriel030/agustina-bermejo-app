package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.repository.CourseSubjectSectionEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SectionOccupancyCounterTest {

    @Mock
    private SubjectSectionEnrollmentRepository programEnrollmentRepository;
    @Mock
    private CourseSubjectSectionEnrollmentRepository courseEnrollmentRepository;
    @InjectMocks
    private SectionOccupancyCounter counter;

    @Test
    void seatsTakenUseTheSameStatusesAsTheEnrollmentCapacityRule() {
        when(programEnrollmentRepository.countBySubjectSectionIdAndStatusIn(5L,
                List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS, EnrollmentStatus.REGULAR)))
                .thenReturn(7L);

        assertThat(counter.countSeatsTaken(SectionKind.PROGRAM, 5L)).isEqualTo(7L);
    }

    @Test
    void courseSectionsAreCountedWithTheirOwnRepository() {
        when(courseEnrollmentRepository.countByCourseSubjectSectionIdAndStatusIn(8L,
                List.of(EnrollmentStatus.WAITLISTED))).thenReturn(3L);

        assertThat(counter.countWaitlisted(SectionKind.COURSE, 8L)).isEqualTo(3L);
    }

    @Test
    void waitlistedCountOfProgramSectionsIsNotZeroByDefault() {
        when(programEnrollmentRepository.countBySubjectSectionIdAndStatusIn(5L,
                List.of(EnrollmentStatus.WAITLISTED))).thenReturn(2L);

        assertThat(counter.countWaitlisted(SectionKind.PROGRAM, 5L)).isEqualTo(2L);
    }
}
