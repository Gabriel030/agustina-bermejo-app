package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.api.CourseCatalogFinder;
import com.red_escuela.backend.academicstructure.api.CourseModuleView;
import com.red_escuela.backend.academicstructure.api.CourseRegistrationView;
import com.red_escuela.backend.academicstructure.api.CourseSectionView;
import com.red_escuela.backend.academicstructure.api.SectionFinder;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.CourseSubject;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.billing.api.EnrollmentDebtGuard;
import com.red_escuela.backend.enrollment.api.EnrollmentWithdrawalBillingPort;
import com.red_escuela.backend.enrollment.api.WithdrawalCause;
import com.red_escuela.backend.enrollment.api.WithdrawnEnrollmentFacts;
import com.red_escuela.backend.enrollment.dto.CourseProgressResponse;
import com.red_escuela.backend.enrollment.dto.CreateCourseEnrollmentRequest;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.exception.AdmissionWindowClosedException;
import com.red_escuela.backend.enrollment.exception.AlreadyEnrolledInCourseException;
import com.red_escuela.backend.enrollment.exception.AmbiguousSectionException;
import com.red_escuela.backend.enrollment.exception.CannotWithdrawException;
import com.red_escuela.backend.enrollment.exception.CourseEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.exception.IncompleteSectionSetupException;
import com.red_escuela.backend.enrollment.exception.InvalidWindowConfigurationException;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.CourseSubjectSectionEnrollmentRepository;
import com.red_escuela.backend.identity.api.StudentEnrollmentEligibility;
import com.red_escuela.backend.identity.api.StudentFinder;
import com.red_escuela.backend.identity.api.StudentRoleGranter;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.shared.domain.Percentage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseEnrollmentRulesTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-02T13:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 2, 13, 0);
    private static final Long COURSE_ID = 11L;
    private static final Long TERM_ID = 3L;

    @Mock
    private CourseEnrollmentRepository courseEnrollmentRepository;
    @Mock
    private StudentFinder studentFinder;
    @Mock
    private StudentEnrollmentEligibility studentEnrollmentEligibility;
    @Mock
    private CourseCatalogFinder courseCatalogFinder;
    @Mock
    private SectionFinder sectionFinder;
    @Mock
    private CourseSubjectSectionEnrollmentRepository subEnrollmentRepository;
    @Mock
    private StudentRoleGranter studentRoleGranter;
    @Mock
    private EnrollmentDebtGuard enrollmentDebtGuard;
    @Mock
    private EnrollmentWithdrawalDateResolver withdrawalDateResolver;
    @Mock
    private EnrollmentWithdrawalBillingPort withdrawalBillingPort;

    private CourseEnrollmentService service;
    private Student student;
    private Course course;

    @BeforeEach
    void setUp() {
        service = new CourseEnrollmentService(courseEnrollmentRepository, studentFinder, studentEnrollmentEligibility, courseCatalogFinder,
                sectionFinder, subEnrollmentRepository, studentRoleGranter, enrollmentDebtGuard, withdrawalDateResolver,
                withdrawalBillingPort, new CatalogAvailabilityGuard(), CLOCK);
        Person person = new Person();
        person.setId(70L);
        student = new Student();
        student.setId(7L);
        student.setPerson(person);
        course = new Course();
        course.setId(COURSE_ID);
    }

    private void givenStudentWithOpenWindow() {
        when(studentFinder.requireByIdForUpdate(7L)).thenReturn(student);
        when(courseCatalogFinder.findRegistration(COURSE_ID, NOW))
                .thenReturn(new CourseRegistrationView(COURSE_ID, TERM_ID, false, true));
    }

    private static CourseSectionView sectionView(long id, long moduleId) {
        return new CourseSectionView(id, moduleId, COURSE_ID, TERM_ID, 30, "Comision " + id);
    }

    private CourseEnrollment courseEnrollment(Long id, EnrollmentStatus status) {
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setId(id);
        enrollment.setStudent(student);
        enrollment.setCourse(course);
        enrollment.setStatus(status);
        enrollment.setEnrollmentDate(NOW.minusDays(10));
        return enrollment;
    }

    private CourseSubjectSectionEnrollment subEnrollment(Long id, Long sectionId, Long moduleId, EnrollmentStatus status) {
        CourseSubject module = new CourseSubject();
        module.setId(moduleId);
        CourseSubjectSection section = new CourseSubjectSection();
        section.setId(sectionId);
        section.setCourseSubject(module);
        CourseSubjectSectionEnrollment sub = new CourseSubjectSectionEnrollment();
        sub.setId(id);
        sub.setCourseSubjectSection(section);
        sub.setStatus(status);
        return sub;
    }

    private void givenWithdrawalDates() {
        when(withdrawalDateResolver.resolveEffectiveDate(any(), any())).thenReturn(LocalDate.of(2026, 3, 2));
        when(withdrawalDateResolver.registrationDate()).thenReturn(LocalDate.of(2026, 3, 2));
        when(withdrawalDateResolver.completionMomentOf(LocalDate.of(2026, 3, 2))).thenReturn(NOW);
    }

    private static EnrollmentWithdrawal voluntaryWithdrawal() {
        return new EnrollmentWithdrawal(null, 99L, WithdrawalCause.VOLUNTARY);
    }

    @Test
    void enrollStopsWhenTheStudentHasOutstandingDebt() {
        when(studentFinder.requireByIdForUpdate(7L)).thenReturn(student);
        doThrow(new IllegalStateException("debt")).when(enrollmentDebtGuard).verifyNoOutstandingDebt(7L);

        assertThatThrownBy(() -> service.enroll(7L, new CreateCourseEnrollmentRequest(COURSE_ID, null)))
                .isInstanceOf(IllegalStateException.class);
        verify(courseEnrollmentRepository, never()).save(any());
    }

    @Test
    void enrollFailsWhenRegistrationWindowIsConfiguredBackwards() {
        when(studentFinder.requireByIdForUpdate(7L)).thenReturn(student);
        when(courseCatalogFinder.findRegistration(COURSE_ID, NOW))
                .thenReturn(new CourseRegistrationView(COURSE_ID, TERM_ID, true, false));

        assertThatThrownBy(() -> service.enroll(7L, new CreateCourseEnrollmentRequest(COURSE_ID, null)))
                .isInstanceOf(InvalidWindowConfigurationException.class);
        verify(courseEnrollmentRepository, never()).save(any());
    }

    @Test
    void enrollFailsOutsideTheRegistrationWindow() {
        when(studentFinder.requireByIdForUpdate(7L)).thenReturn(student);
        when(courseCatalogFinder.findRegistration(COURSE_ID, NOW))
                .thenReturn(new CourseRegistrationView(COURSE_ID, TERM_ID, false, false));

        assertThatThrownBy(() -> service.enroll(7L, new CreateCourseEnrollmentRequest(COURSE_ID, null)))
                .isInstanceOf(AdmissionWindowClosedException.class);
        verify(courseEnrollmentRepository, never()).save(any());
    }

    @Test
    void enrollFailsWhenTheStudentAlreadyHasAnActiveEnrollmentInTheCourse() {
        givenStudentWithOpenWindow();
        when(courseEnrollmentRepository.findByStudentIdAndCourseIdAndStatusIn(7L, COURSE_ID,
                List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS, EnrollmentStatus.WAITLISTED)))
                .thenReturn(List.of(courseEnrollment(1L, EnrollmentStatus.WAITLISTED)));

        assertThatThrownBy(() -> service.enroll(7L, new CreateCourseEnrollmentRequest(COURSE_ID, null)))
                .isInstanceOf(AlreadyEnrolledInCourseException.class);
        verify(courseEnrollmentRepository, never()).save(any());
    }

    @Test
    void enrollFailsWhenAModuleHasNoSectionForTheTerm() {
        givenStudentWithOpenWindow();
        when(courseEnrollmentRepository.findByStudentIdAndCourseIdAndStatusIn(any(), any(), any())).thenReturn(List.of());
        when(courseCatalogFinder.findActiveModules(COURSE_ID)).thenReturn(List.of(new CourseModuleView(21L, "Modulo 1")));
        when(courseCatalogFinder.findActiveSectionsOfModule(21L)).thenReturn(List.of(
                new CourseSectionView(31L, 21L, COURSE_ID, 99L, 30, "Otro ciclo")));

        assertThatThrownBy(() -> service.enroll(7L, new CreateCourseEnrollmentRequest(COURSE_ID, null)))
                .isInstanceOf(IncompleteSectionSetupException.class);
        verify(courseEnrollmentRepository, never()).save(any());
    }

    @Test
    void enrollFailsWhenAModuleHasSeveralSectionsAndNoneWasChosen() {
        givenStudentWithOpenWindow();
        when(courseEnrollmentRepository.findByStudentIdAndCourseIdAndStatusIn(any(), any(), any())).thenReturn(List.of());
        when(courseCatalogFinder.findActiveModules(COURSE_ID)).thenReturn(List.of(new CourseModuleView(21L, "Modulo 1")));
        when(courseCatalogFinder.findActiveSectionsOfModule(21L)).thenReturn(List.of(sectionView(31L, 21L), sectionView(32L, 21L)));

        assertThatThrownBy(() -> service.enroll(7L, new CreateCourseEnrollmentRequest(COURSE_ID, null)))
                .isInstanceOf(AmbiguousSectionException.class);
        assertThatThrownBy(() -> service.enroll(7L, new CreateCourseEnrollmentRequest(COURSE_ID, Map.of())))
                .isInstanceOf(AmbiguousSectionException.class);
    }

    @Test
    void enrollFailsWhenTheChosenSectionDoesNotBelongToTheModule() {
        givenStudentWithOpenWindow();
        when(courseEnrollmentRepository.findByStudentIdAndCourseIdAndStatusIn(any(), any(), any())).thenReturn(List.of());
        when(courseCatalogFinder.findActiveModules(COURSE_ID)).thenReturn(List.of(new CourseModuleView(21L, "Modulo 1")));
        when(courseCatalogFinder.findActiveSectionsOfModule(21L)).thenReturn(List.of(sectionView(31L, 21L), sectionView(32L, 21L)));

        assertThatThrownBy(() -> service.enroll(7L, new CreateCourseEnrollmentRequest(COURSE_ID, Map.of(21L, 999L))))
                .isInstanceOf(AmbiguousSectionException.class);
        verify(courseEnrollmentRepository, never()).save(any());
    }

    @Test
    void enrollPlacesTheStudentOnTheWaitlistWhenTheCourseHasNoSeatsLeft() {
        givenStudentWithOpenWindow();
        when(courseEnrollmentRepository.findByStudentIdAndCourseIdAndStatusIn(any(), any(), any())).thenReturn(List.of());
        when(courseCatalogFinder.findActiveModules(COURSE_ID)).thenReturn(List.of());
        when(courseCatalogFinder.findCourseEntity(COURSE_ID)).thenReturn(course);
        when(courseEnrollmentRepository.countByCourseIdAndStatusIn(COURSE_ID, List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS)))
                .thenReturn(30L);
        when(courseCatalogFinder.hasCapacityFor(COURSE_ID, 30L)).thenReturn(false);
        when(courseEnrollmentRepository.save(any(CourseEnrollment.class))).thenAnswer(inv -> inv.getArgument(0));

        CourseEnrollment saved = service.enroll(7L, new CreateCourseEnrollmentRequest(COURSE_ID, null));

        assertThat(saved.getStatus()).isEqualTo(EnrollmentStatus.WAITLISTED);
        assertThat(saved.getEnrollmentDate()).isEqualTo(NOW);
        verify(courseCatalogFinder).lockCourse(COURSE_ID);
    }

    @Test
    void enrollSelfServiceGrantsTheStudentRoleToTheEnrolledPerson() {
        givenStudentWithOpenWindow();
        when(courseEnrollmentRepository.findByStudentIdAndCourseIdAndStatusIn(any(), any(), any())).thenReturn(List.of());
        when(courseCatalogFinder.findActiveModules(COURSE_ID)).thenReturn(List.of());
        when(courseCatalogFinder.findCourseEntity(COURSE_ID)).thenReturn(course);
        when(courseCatalogFinder.hasCapacityFor(COURSE_ID, 0L)).thenReturn(true);
        when(courseEnrollmentRepository.save(any(CourseEnrollment.class))).thenAnswer(inv -> inv.getArgument(0));

        CourseEnrollment saved = service.enrollSelfService(7L, new CreateCourseEnrollmentRequest(COURSE_ID, null));

        assertThat(saved.getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
        verify(studentRoleGranter).grantStudentRoleIfMissing(student.getPerson());
    }

    @ParameterizedTest
    @EnumSource(value = EnrollmentStatus.class, names = {"IN_PROGRESS", "REGULAR", "APPROVED", "FAILED", "WITHDRAWN", "WAITLIST_EXPIRED"})
    void withdrawRejectsCourseEnrollmentsThatAreNeitherEnrolledNorWaitlisted(EnrollmentStatus status) {
        CourseEnrollment enrollment = courseEnrollment(1L, status);
        when(courseEnrollmentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(enrollment));

        assertThatThrownBy(() -> service.withdraw(1L, voluntaryWithdrawal())).isInstanceOf(CannotWithdrawException.class);
        assertThat(enrollment.getStatus()).isEqualTo(status);
        verify(courseEnrollmentRepository, never()).save(any());
        verify(withdrawalBillingPort, never()).registerWithdrawalForBilling(any());
    }

    @Test
    void withdrawOfEnrolledStudentReportsBillingCancelsModulesAndPromotesWaitlists() {
        CourseEnrollment enrollment = courseEnrollment(1L, EnrollmentStatus.ENROLLED);
        CourseSubjectSectionEnrollment activeModule = subEnrollment(100L, 31L, 21L, EnrollmentStatus.ENROLLED);
        CourseSubjectSectionEnrollment waitlistedModule = subEnrollment(101L, 32L, 22L, EnrollmentStatus.WAITLISTED);
        CourseSubjectSectionEnrollment approvedModule = subEnrollment(102L, 33L, 23L, EnrollmentStatus.APPROVED);
        CourseEnrollment nextInCourse = courseEnrollment(2L, EnrollmentStatus.WAITLISTED);
        CourseSubjectSectionEnrollment nextInSection = subEnrollment(200L, 31L, 21L, EnrollmentStatus.WAITLISTED);
        when(courseEnrollmentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(enrollment));
        givenWithdrawalDates();
        when(subEnrollmentRepository.findByCourseEnrollmentId(1L)).thenReturn(List.of(activeModule, waitlistedModule, approvedModule));
        when(sectionFinder.findCourseSection(31L)).thenReturn(sectionView(31L, 21L));
        when(subEnrollmentRepository.findFirstByCourseSubjectSectionIdAndStatusOrderByEnrolledAtAsc(31L, EnrollmentStatus.WAITLISTED))
                .thenReturn(Optional.of(nextInSection));
        when(courseCatalogFinder.hasCapacityFor(COURSE_ID, 0L)).thenReturn(true);
        when(courseEnrollmentRepository.findFirstByCourseIdAndStatusOrderByEnrollmentDateAsc(COURSE_ID, EnrollmentStatus.WAITLISTED))
                .thenReturn(Optional.of(nextInCourse));

        service.withdraw(1L, voluntaryWithdrawal());

        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.WITHDRAWN);
        assertThat(activeModule.getStatus()).isEqualTo(EnrollmentStatus.WITHDRAWN);
        assertThat(activeModule.getDroppedAt()).isEqualTo(NOW);
        assertThat(waitlistedModule.getStatus()).isEqualTo(EnrollmentStatus.WITHDRAWN);
        assertThat(approvedModule.getStatus()).isEqualTo(EnrollmentStatus.APPROVED);
        assertThat(approvedModule.getDroppedAt()).isNull();
        assertThat(nextInSection.getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
        assertThat(nextInCourse.getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
        ArgumentCaptor<WithdrawnEnrollmentFacts> facts = ArgumentCaptor.forClass(WithdrawnEnrollmentFacts.class);
        verify(withdrawalBillingPort).registerWithdrawalForBilling(facts.capture());
        assertThat(facts.getValue().courseEnrollmentId()).isEqualTo(1L);
        assertThat(facts.getValue().studentId()).isEqualTo(7L);
        assertThat(facts.getValue().cause()).isEqualTo(WithdrawalCause.VOLUNTARY);
        verify(sectionFinder).lockCourseSection(31L);
        verify(sectionFinder, never()).lockCourseSection(32L);
        verify(courseCatalogFinder).lockCourse(COURSE_ID);
    }

    @Test
    void withdrawLocksTheCourseFirstAndThenTheSectionsInAscendingIdOrder() {
        CourseEnrollment enrollment = courseEnrollment(1L, EnrollmentStatus.ENROLLED);
        CourseSubjectSectionEnrollment higherSection = subEnrollment(100L, 33L, 23L, EnrollmentStatus.ENROLLED);
        CourseSubjectSectionEnrollment lowerSection = subEnrollment(101L, 31L, 21L, EnrollmentStatus.IN_PROGRESS);
        CourseSubjectSectionEnrollment middleSection = subEnrollment(102L, 32L, 22L, EnrollmentStatus.ENROLLED);
        when(courseEnrollmentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(enrollment));
        givenWithdrawalDates();
        when(subEnrollmentRepository.findByCourseEnrollmentId(1L)).thenReturn(List.of(higherSection, lowerSection, middleSection));
        when(sectionFinder.findCourseSection(any())).thenAnswer(inv -> sectionView(inv.getArgument(0), 21L));

        service.withdraw(1L, voluntaryWithdrawal());

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(courseCatalogFinder, sectionFinder);
        order.verify(courseCatalogFinder).lockCourse(COURSE_ID);
        order.verify(sectionFinder).lockCourseSection(31L);
        order.verify(sectionFinder).lockCourseSection(32L);
        order.verify(sectionFinder).lockCourseSection(33L);
        verify(courseCatalogFinder, org.mockito.Mockito.times(1)).lockCourse(any());
        verify(sectionFinder, org.mockito.Mockito.times(3)).lockCourseSection(any());
    }

    @Test
    void withdrawOfWaitlistedStudentDoesNotFreeASeatForTheCourseWaitlist() {
        CourseEnrollment enrollment = courseEnrollment(1L, EnrollmentStatus.WAITLISTED);
        when(courseEnrollmentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(enrollment));
        givenWithdrawalDates();
        when(subEnrollmentRepository.findByCourseEnrollmentId(1L)).thenReturn(List.of());

        service.withdraw(1L, voluntaryWithdrawal());

        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.WITHDRAWN);
        verify(courseCatalogFinder, never()).lockCourse(any());
        verify(courseEnrollmentRepository, never()).findFirstByCourseIdAndStatusOrderByEnrollmentDateAsc(any(), any());
    }

    @Test
    void withdrawDoesNotPromoteTheCourseWaitlistWhenTheCourseIsStillFull() {
        CourseEnrollment enrollment = courseEnrollment(1L, EnrollmentStatus.ENROLLED);
        when(courseEnrollmentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(enrollment));
        givenWithdrawalDates();
        when(subEnrollmentRepository.findByCourseEnrollmentId(1L)).thenReturn(List.of());
        when(courseEnrollmentRepository.countByCourseIdAndStatusIn(COURSE_ID, List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS)))
                .thenReturn(2L);
        when(courseCatalogFinder.hasCapacityFor(COURSE_ID, 2L)).thenReturn(false);

        service.withdraw(1L, voluntaryWithdrawal());

        verify(courseEnrollmentRepository, never()).findFirstByCourseIdAndStatusOrderByEnrollmentDateAsc(any(), any());
    }

    @Test
    void withdrawDoesNotPromoteTheSectionWaitlistWhenTheSectionIsStillFull() {
        CourseEnrollment enrollment = courseEnrollment(1L, EnrollmentStatus.ENROLLED);
        CourseSubjectSectionEnrollment activeModule = subEnrollment(100L, 31L, 21L, EnrollmentStatus.ENROLLED);
        when(courseEnrollmentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(enrollment));
        givenWithdrawalDates();
        when(subEnrollmentRepository.findByCourseEnrollmentId(1L)).thenReturn(List.of(activeModule));
        when(sectionFinder.findCourseSection(31L)).thenReturn(new CourseSectionView(31L, 21L, COURSE_ID, TERM_ID, 1, "Comision 31"));
        when(subEnrollmentRepository.countByCourseSubjectSectionIdAndStatusIn(31L,
                List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS, EnrollmentStatus.REGULAR))).thenReturn(1L);

        service.withdraw(1L, voluntaryWithdrawal());

        verify(subEnrollmentRepository, never()).findFirstByCourseSubjectSectionIdAndStatusOrderByEnrolledAtAsc(any(), any());
    }

    @Test
    void reEnrollingInAPreviouslyWithdrawnSectionTakesTheCurrentDate() {
        givenStudentWithOpenWindow();
        when(courseEnrollmentRepository.findByStudentIdAndCourseIdAndStatusIn(any(), any(), any())).thenReturn(List.of());
        when(courseCatalogFinder.findActiveModules(COURSE_ID)).thenReturn(List.of(new CourseModuleView(21L, "Modulo 1")));
        when(courseCatalogFinder.findActiveSectionsOfModule(21L)).thenReturn(List.of(sectionView(31L, 21L)));
        when(courseCatalogFinder.findCourseEntity(COURSE_ID)).thenReturn(course);
        when(courseCatalogFinder.hasCapacityFor(COURSE_ID, 0L)).thenReturn(true);
        when(courseEnrollmentRepository.save(any(CourseEnrollment.class))).thenAnswer(inv -> inv.getArgument(0));
        CourseSubjectSectionEnrollment previous = subEnrollment(100L, 31L, 21L, EnrollmentStatus.WITHDRAWN);
        previous.setEnrolledAt(NOW.minusYears(1));
        when(subEnrollmentRepository.findByStudentIdAndCourseSubjectSectionId(7L, 31L)).thenReturn(Optional.of(previous));

        service.enroll(7L, new CreateCourseEnrollmentRequest(COURSE_ID, null));

        assertThat(previous.getEnrolledAt()).isEqualTo(NOW);
    }

    @Test
    void getAndProgressFailForUnknownEnrollment() {
        when(courseEnrollmentRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(1L)).isInstanceOf(CourseEnrollmentNotFoundException.class);
        assertThatThrownBy(() -> service.calculateProgress(1L)).isInstanceOf(CourseEnrollmentNotFoundException.class);
    }

    @Test
    void listByStudentReturnsTheCourseEnrollmentsOfThatStudent() {
        List<CourseEnrollment> expected = List.of(courseEnrollment(1L, EnrollmentStatus.ENROLLED));
        when(courseEnrollmentRepository.findByStudentId(7L)).thenReturn(expected);

        assertThat(service.listByStudent(7L)).isEqualTo(expected);
    }

    @Test
    void progressListsOnlyTheModulesWithoutAnApprovedEnrollment() {
        CourseEnrollment enrollment = courseEnrollment(1L, EnrollmentStatus.IN_PROGRESS);
        when(courseEnrollmentRepository.findById(1L)).thenReturn(Optional.of(enrollment));
        when(courseCatalogFinder.findActiveModules(COURSE_ID))
                .thenReturn(List.of(new CourseModuleView(21L, "Modulo 1"), new CourseModuleView(22L, "Modulo 2"), new CourseModuleView(23L, "Modulo 3")));
        when(subEnrollmentRepository.findByCourseEnrollmentId(1L)).thenReturn(List.of(
                subEnrollment(100L, 31L, 21L, EnrollmentStatus.APPROVED),
                subEnrollment(101L, 32L, 22L, EnrollmentStatus.FAILED)));

        CourseProgressResponse progress = service.calculateProgress(1L);

        assertThat(progress.modulosPendientes()).containsExactly("Modulo 2", "Modulo 3");
        assertThat(progress.elegibleParaCompletar()).isFalse();
    }

    @Test
    void progressIsCompleteWhenEveryActiveModuleIsApproved() {
        CourseEnrollment enrollment = courseEnrollment(1L, EnrollmentStatus.IN_PROGRESS);
        when(courseEnrollmentRepository.findById(1L)).thenReturn(Optional.of(enrollment));
        when(courseCatalogFinder.findActiveModules(COURSE_ID)).thenReturn(List.of(new CourseModuleView(21L, "Modulo 1")));
        when(subEnrollmentRepository.findByCourseEnrollmentId(1L))
                .thenReturn(List.of(subEnrollment(100L, 31L, 21L, EnrollmentStatus.APPROVED)));

        assertThat(service.calculateProgress(1L).elegibleParaCompletar()).isTrue();
    }

    @Test
    void approvedModulesPercentageCountsOnlyApprovedActiveModules() {
        CourseEnrollment enrollment = courseEnrollment(1L, EnrollmentStatus.IN_PROGRESS);
        when(courseCatalogFinder.findActiveModules(COURSE_ID))
                .thenReturn(List.of(new CourseModuleView(21L, "Modulo 1"), new CourseModuleView(22L, "Modulo 2")));
        when(subEnrollmentRepository.findByCourseEnrollmentId(1L)).thenReturn(List.of(
                subEnrollment(100L, 31L, 21L, EnrollmentStatus.APPROVED),
                subEnrollment(101L, 32L, 22L, EnrollmentStatus.IN_PROGRESS),
                subEnrollment(102L, 34L, 99L, EnrollmentStatus.APPROVED)));

        assertThat(service.calculateApprovedModulesPercentage(enrollment)).isEqualTo(Percentage.ratio(1, 2));
    }

    @Test
    void approvedModulesPercentageIsEmptyWhenTheCourseHasNoActiveModules() {
        CourseEnrollment enrollment = courseEnrollment(1L, EnrollmentStatus.IN_PROGRESS);
        when(courseCatalogFinder.findActiveModules(COURSE_ID)).thenReturn(List.of());
        when(subEnrollmentRepository.findByCourseEnrollmentId(1L)).thenReturn(List.of());

        assertThat(service.calculateApprovedModulesPercentage(enrollment)).isEmpty();
    }
}
