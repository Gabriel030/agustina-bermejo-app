package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.api.CourseSectionView;
import com.red_escuela.backend.academicstructure.api.SectionFinder;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.enrollment.dto.WaitlistKind;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseSectionEnrollmentServiceTest {

    private static final List<EnrollmentStatus> SEAT_HOLDING =
            List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS, EnrollmentStatus.REGULAR, EnrollmentStatus.APPROVAL_PENDING);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-04T15:30:00Z"), ZoneOffset.UTC);

    @Mock
    private CourseSubjectSectionEnrollmentRepository repository;
    @Mock
    private SectionFinder sectionFinder;
    @Mock
    private WaitlistPromoter waitlistPromoter;

    private CourseSectionEnrollmentService service;
    private CourseSubjectSection section;

    @BeforeEach
    void setUp() {
        service = new CourseSectionEnrollmentService(repository, waitlistPromoter, CLOCK);
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

        service.withdraw(1L, EnrollmentChange.byStaff("Baja de prueba por secretaría", 99L));

        assertThat(active.getStatus()).isEqualTo(EnrollmentStatus.WITHDRAWN);
        assertThat(active.getDroppedAt()).isEqualTo(LocalDateTime.of(2026, 5, 4, 15, 30));
        verify(repository).save(active);
    }

    @ParameterizedTest
    @EnumSource(value = EnrollmentStatus.class, names = {"REGULAR", "APPROVED", "FAILED", "WITHDRAWN", "WAITLIST_EXPIRED"})
    void withdrawRejectsEnrollmentsThatAreNotActive(EnrollmentStatus status) {
        CourseSubjectSectionEnrollment inactive = enrollment(1L, status);
        when(repository.findById(1L)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.withdraw(1L, EnrollmentChange.byStaff("Baja de prueba por secretaría", 99L))).isInstanceOf(CannotWithdrawException.class);
        assertThat(inactive.getStatus()).isEqualTo(status);
        assertThat(inactive.getDroppedAt()).isNull();
        verify(repository, never()).save(inactive);
        verifyNoInteractions(waitlistPromoter);
    }

    @Test
    void withdrawFailsWhenEnrollmentDoesNotExist() {
        when(repository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.withdraw(1L, EnrollmentChange.byStaff("Baja de prueba por secretaría", 99L))).isInstanceOf(SectionEnrollmentNotFoundException.class);
    }



    @Test
    void withdrawAsksThePromoterForTheFreedSectionAfterSavingTheLeavingEnrollment() {
        CourseSubjectSectionEnrollment leaving = enrollment(1L, EnrollmentStatus.ENROLLED);
        when(repository.findById(1L)).thenReturn(Optional.of(leaving));

        service.withdraw(1L, EnrollmentChange.byStaff("Baja de prueba por secretaría", 99L));

        InOrder order = inOrder(repository, waitlistPromoter);
        order.verify(repository).save(leaving);
        order.verify(waitlistPromoter).promoteNext(WaitlistKind.COURSE_SECTION, 40L);
    }

    @Test
    void withdrawingAWaitlistedEnrollmentLeavesTheQueueWithoutLockingOrPromotingAnyone() {
        CourseSubjectSectionEnrollment waiting = enrollment(1L, EnrollmentStatus.WAITLISTED);
        when(repository.findById(1L)).thenReturn(Optional.of(waiting));

        service.withdraw(1L, EnrollmentChange.byStaff("Baja de prueba por secretaría", 99L));

        assertThat(waiting.getStatus()).isEqualTo(EnrollmentStatus.WITHDRAWN);
        assertThat(waiting.getDroppedAt()).isEqualTo(LocalDateTime.of(2026, 5, 4, 15, 30));
        verify(repository).save(waiting);
        verifyNoInteractions(waitlistPromoter);
    }


}
