package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.api.CourseCatalogFinder;
import com.red_escuela.backend.academicstructure.api.SectionFinder;
import com.red_escuela.backend.enrollment.api.EnrollmentWithdrawalBillingPort;
import com.red_escuela.backend.enrollment.api.WithdrawalCause;
import com.red_escuela.backend.enrollment.api.WithdrawnEnrollmentFacts;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.exception.CannotWithdrawException;
import com.red_escuela.backend.enrollment.exception.WithdrawalDateOutOfAllowedRangeException;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.CourseSubjectSectionEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.identity.entity.Student;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("D-296")
@Tag("D-249")
@Tag("D-142")
class EnrollmentWithdrawalBillingPortInvocationTest {

    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final LocalDateTime NOW = LocalDateTime.of(2027, 5, 15, 12, 0);
    private static final Long STUDENT_ID = 41L;

    private final Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
    private final EnrollmentWithdrawalBillingPort port = mock(EnrollmentWithdrawalBillingPort.class);
    private final ProgramEnrollmentRepository programRepository = mock(ProgramEnrollmentRepository.class);
    private final CourseEnrollmentRepository courseRepository = mock(CourseEnrollmentRepository.class);
    private ProgramEnrollmentService programService;
    private CourseEnrollmentService courseService;
    private ProgramEnrollment programEnrollment;
    private CourseEnrollment courseEnrollment;

    @BeforeEach
    void setUp() {
        EnrollmentWithdrawalDateResolver resolver = new EnrollmentWithdrawalDateResolver(clock);
        programService = new ProgramEnrollmentService(programRepository, null, null, null, null, null, null, null, null,
                resolver, port, clock);
        CourseCatalogFinder courses = mock(CourseCatalogFinder.class);
        courseService = new CourseEnrollmentService(courseRepository, null, courses, mock(SectionFinder.class), mock(CourseSubjectSectionEnrollmentRepository.class), null, null,
                resolver, port, clock);
        Student student = new Student();
        student.setId(STUDENT_ID);
        programEnrollment = new ProgramEnrollment();
        programEnrollment.setId(10L);
        programEnrollment.setStudent(student);
        programEnrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
        programEnrollment.setEnrollmentDate(LocalDateTime.of(2027, 3, 1, 10, 0));
        when(programRepository.findById(10L)).thenReturn(Optional.of(programEnrollment));
        Course course = new Course();
        course.setId(5L);
        courseEnrollment = new CourseEnrollment();
        courseEnrollment.setId(20L);
        courseEnrollment.setStudent(student);
        courseEnrollment.setCourse(course);
        courseEnrollment.setStatus(EnrollmentStatus.WAITLISTED);
        courseEnrollment.setEnrollmentDate(LocalDateTime.of(2027, 3, 1, 10, 0));
        when(courseRepository.findById(20L)).thenReturn(Optional.of(courseEnrollment));
    }

    @Test
    void programWithdrawalNotifiesBillingWithTheInformedDateAndTheVoluntaryCause() {
        programService.withdraw(10L, EnrollmentWithdrawal.voluntaryRegisteredBy(LocalDate.of(2027, 4, 28), 99L));

        verify(port).registerWithdrawalForBilling(WithdrawnEnrollmentFacts.ofProgramEnrollment(STUDENT_ID, 10L, LocalDate.of(2027, 4, 28), LocalDate.of(2027, 5, 15), WithdrawalCause.VOLUNTARY));
        assertThat(programEnrollment.getStatus()).isEqualTo(ProgramEnrollmentStatus.WITHDRAWN);
        assertThat(programEnrollment.getWithdrawalEffectiveDate()).isEqualTo(LocalDate.of(2027, 4, 28));
        assertThat(programEnrollment.getWithdrawnByPersonId()).isEqualTo(99L);
        assertThat(programEnrollment.getCompletionDate().toLocalDate()).isEqualTo(LocalDate.of(2027, 4, 28));
    }

    @Test
    void programWithdrawalForNonpaymentNotifiesBillingWithTheNonpaymentCauseAndNoActor() {
        programService.withdraw(10L, EnrollmentWithdrawal.forNonpayment());

        verify(port).registerWithdrawalForBilling(WithdrawnEnrollmentFacts.ofProgramEnrollment(STUDENT_ID, 10L, LocalDate.of(2027, 5, 15), LocalDate.of(2027, 5, 15), WithdrawalCause.NONPAYMENT));
        assertThat(programEnrollment.getWithdrawnByPersonId()).isNull();
    }

    @Test
    void programWithdrawalWithDateOutOfRangeChangesNothingAndDoesNotNotifyBilling() {
        assertThatThrownBy(() -> programService.withdraw(10L, EnrollmentWithdrawal.voluntaryRegisteredBy(LocalDate.of(2027, 3, 31), 99L)))
                .isInstanceOf(WithdrawalDateOutOfAllowedRangeException.class);

        assertThat(programEnrollment.getStatus()).isEqualTo(ProgramEnrollmentStatus.ENROLLED);
        assertThat(programEnrollment.getWithdrawalEffectiveDate()).isNull();
        verifyNoInteractions(port);
        verify(programRepository, never()).save(any());
    }

    @Test
    void withdrawingAnAlreadyWithdrawnProgramEnrollmentDoesNotNotifyBilling() {
        programEnrollment.setStatus(ProgramEnrollmentStatus.WITHDRAWN);

        assertThatThrownBy(() -> programService.withdraw(10L, EnrollmentWithdrawal.voluntaryRegisteredBy(null, 99L))).isInstanceOf(CannotWithdrawException.class);

        verifyNoInteractions(port);
    }

    @Test
    void courseWithdrawalNotifiesBillingWithTheEffectiveDate() {
        courseService.withdraw(20L, EnrollmentWithdrawal.voluntaryRegisteredBy(null, 99L));

        verify(port).registerWithdrawalForBilling(WithdrawnEnrollmentFacts.ofCourseEnrollment(STUDENT_ID, 20L, LocalDate.of(2027, 5, 15), LocalDate.of(2027, 5, 15), WithdrawalCause.VOLUNTARY));
        assertThat(courseEnrollment.getStatus()).isEqualTo(EnrollmentStatus.WITHDRAWN);
        assertThat(courseEnrollment.getWithdrawalEffectiveDate()).isEqualTo(LocalDate.of(2027, 5, 15));
        assertThat(courseEnrollment.getWithdrawnByPersonId()).isEqualTo(99L);
    }

    @Test
    void courseWithdrawalWithDateOutOfRangeChangesNothingAndDoesNotNotifyBilling() {
        assertThatThrownBy(() -> courseService.withdraw(20L, EnrollmentWithdrawal.voluntaryRegisteredBy(LocalDate.of(2027, 5, 16), 99L)))
                .isInstanceOf(WithdrawalDateOutOfAllowedRangeException.class);

        assertThat(courseEnrollment.getStatus()).isEqualTo(EnrollmentStatus.WAITLISTED);
        verifyNoInteractions(port);
    }
}
