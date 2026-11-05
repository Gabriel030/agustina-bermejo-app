package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.api.SectionFinder;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.exception.CannotWithdrawException;
import com.red_escuela.backend.enrollment.exception.SectionEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.repository.CourseSubjectSectionEnrollmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseSectionEnrollmentServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-04T15:30:00Z"), ZoneOffset.UTC);

    @Mock
    private CourseSubjectSectionEnrollmentRepository repository;
    @Mock
    private SectionFinder sectionFinder;

    private CourseSectionEnrollmentService service;
    private CourseSubjectSection section;

    @BeforeEach
    void setUp() {
        service = new CourseSectionEnrollmentService(repository, sectionFinder, CLOCK);
        section = new CourseSubjectSection();
        section.setId(40L);
    }

    private CourseSubjectSectionEnrollment enrollment(Long id, EnrollmentStatus status) {
        CourseSubjectSectionEnrollment enrollment = new CourseSubjectSectionEnrollment();
        enrollment.setId(id);
        enrollment.setStatus(status);
        enrollment.setCourseSubjectSection(section);
        return enrollment;
    }

    @Test
    void listByEnrollmentReturnsTheSectionEnrollmentsOfThatCourseEnrollment() {
        List<CourseSubjectSectionEnrollment> expected = List.of(enrollment(1L, EnrollmentStatus.ENROLLED));
        when(repository.findByCourseEnrollmentId(9L)).thenReturn(expected);

        assertThat(service.listByEnrollment(9L)).isEqualTo(expected);
    }

    @ParameterizedTest
    @EnumSource(value = EnrollmentStatus.class, names = {"ENROLLED", "IN_PROGRESS"})
    void withdrawMarksActiveEnrollmentAsWithdrawnWithClockTimestamp(EnrollmentStatus status) {
        CourseSubjectSectionEnrollment active = enrollment(1L, status);
        when(repository.findById(1L)).thenReturn(Optional.of(active));
        when(repository.findFirstByCourseSubjectSectionIdAndStatusOrderByEnrolledAtAsc(40L, EnrollmentStatus.WAITLISTED))
                .thenReturn(Optional.empty());

        service.withdraw(1L);

        assertThat(active.getStatus()).isEqualTo(EnrollmentStatus.WITHDRAWN);
        assertThat(active.getDroppedAt()).isEqualTo(LocalDateTime.of(2026, 5, 4, 15, 30));
        verify(repository).save(active);
    }

    @ParameterizedTest
    @EnumSource(value = EnrollmentStatus.class, names = {"REGULAR", "APPROVED", "FAILED", "WITHDRAWN", "WAITLISTED", "WAITLIST_EXPIRED"})
    void withdrawRejectsEnrollmentsThatAreNotActive(EnrollmentStatus status) {
        CourseSubjectSectionEnrollment inactive = enrollment(1L, status);
        when(repository.findById(1L)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.withdraw(1L)).isInstanceOf(CannotWithdrawException.class);
        assertThat(inactive.getStatus()).isEqualTo(status);
        assertThat(inactive.getDroppedAt()).isNull();
        verify(repository, never()).save(inactive);
        verify(sectionFinder, never()).lockCourseSection(40L);
    }

    @Test
    void withdrawFailsWhenEnrollmentDoesNotExist() {
        when(repository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.withdraw(1L)).isInstanceOf(SectionEnrollmentNotFoundException.class);
    }

    @Test
    void withdrawPromotesOldestWaitlistedStudentAfterLockingTheSection() {
        CourseSubjectSectionEnrollment leaving = enrollment(1L, EnrollmentStatus.ENROLLED);
        CourseSubjectSectionEnrollment next = enrollment(2L, EnrollmentStatus.WAITLISTED);
        when(repository.findById(1L)).thenReturn(Optional.of(leaving));
        when(repository.findFirstByCourseSubjectSectionIdAndStatusOrderByEnrolledAtAsc(40L, EnrollmentStatus.WAITLISTED))
                .thenReturn(Optional.of(next));

        service.withdraw(1L);

        assertThat(next.getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
        InOrder order = inOrder(repository, sectionFinder);
        order.verify(repository).save(leaving);
        order.verify(sectionFinder).lockCourseSection(40L);
        order.verify(repository).save(next);
    }

    @Test
    void withdrawWithEmptyWaitlistDoesNotPromoteAnyone() {
        CourseSubjectSectionEnrollment leaving = enrollment(1L, EnrollmentStatus.IN_PROGRESS);
        when(repository.findById(1L)).thenReturn(Optional.of(leaving));
        when(repository.findFirstByCourseSubjectSectionIdAndStatusOrderByEnrolledAtAsc(40L, EnrollmentStatus.WAITLISTED))
                .thenReturn(Optional.empty());

        service.withdraw(1L);

        verify(repository).save(leaving);
        verify(sectionFinder).lockCourseSection(40L);
    }
}
