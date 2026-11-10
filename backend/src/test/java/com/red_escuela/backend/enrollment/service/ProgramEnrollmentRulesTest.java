package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.api.AcademicReferenceFinder;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.attendance.api.AttendanceRiskView;
import com.red_escuela.backend.attendance.api.AttendanceRiskReader;
import com.red_escuela.backend.billing.api.EnrollmentDebtGuard;
import com.red_escuela.backend.billing.entity.ProgramCohort;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.exception.CohortAdmissionWindowClosedException;
import com.red_escuela.backend.billing.exception.ProgramCohortNotFoundException;
import com.red_escuela.backend.billing.exception.ProgramYearBillingTemplateMismatchException;
import com.red_escuela.backend.billing.exception.ProgramYearBillingTemplateNotFoundException;
import com.red_escuela.backend.billing.repository.ProgramCohortRepository;
import com.red_escuela.backend.billing.repository.ProgramYearBillingTemplateRepository;
import com.red_escuela.backend.enrollment.api.EnrollmentWithdrawalBillingPort;
import com.red_escuela.backend.enrollment.api.ProgramProgressReader;
import com.red_escuela.backend.enrollment.api.ProgramProgressView;
import com.red_escuela.backend.enrollment.api.ElectiveGroupProgressView;
import com.red_escuela.backend.enrollment.api.WithdrawalCause;
import com.red_escuela.backend.enrollment.api.WithdrawnEnrollmentFacts;
import com.red_escuela.backend.enrollment.dto.CreateProgramEnrollmentRequest;
import com.red_escuela.backend.enrollment.dto.ProgramProgressResponse;
import com.red_escuela.backend.enrollment.dto.SubjectAttendanceSummaryResponse;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.exception.AlreadyEnrolledInProgramException;
import com.red_escuela.backend.enrollment.exception.CannotWithdrawException;
import com.red_escuela.backend.enrollment.exception.InvalidWindowConfigurationException;
import com.red_escuela.backend.enrollment.exception.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository;
import com.red_escuela.backend.identity.api.StudentEnrollmentEligibility;
import com.red_escuela.backend.identity.api.StudentFinder;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.shared.domain.Grade;
import com.red_escuela.backend.shared.domain.Percentage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProgramEnrollmentRulesTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-02T13:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 2, 13, 0);

    @Mock
    private ProgramEnrollmentRepository programEnrollmentRepository;
    @Mock
    private StudentFinder studentFinder;
    @Mock
    private StudentEnrollmentEligibility studentEnrollmentEligibility;
    @Mock
    private AcademicReferenceFinder academicReferenceFinder;
    @Mock
    private ProgramProgressReader programProgressReader;
    @Mock
    private SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository;
    @Mock
    private ProgramCohortRepository programCohortRepository;
    @Mock
    private ProgramYearBillingTemplateRepository templateRepository;
    @Mock
    private AttendanceRiskReader attendanceRiskUseCase;
    @Mock
    private EnrollmentDebtGuard enrollmentDebtGuard;

    @Mock
    private ProgramApplicationDocumentCompletenessGuard documentCompletenessGuard;
    @Mock
    private EnrollmentWithdrawalDateResolver withdrawalDateResolver;
    @Mock
    private EnrollmentWithdrawalBillingPort withdrawalBillingPort;

    private ProgramEnrollmentService service;
    private Student student;
    private Program program;

    @BeforeEach
    void setUp() {
        service = new ProgramEnrollmentService(programEnrollmentRepository, studentFinder, studentEnrollmentEligibility,
                academicReferenceFinder, programProgressReader, subjectSectionEnrollmentRepository, programCohortRepository,
                templateRepository, attendanceRiskUseCase, enrollmentDebtGuard, withdrawalDateResolver, withdrawalBillingPort, documentCompletenessGuard, CLOCK);
        student = new Student();
        student.setId(7L);
        program = new Program();
        program.setId(4L);
    }

    private ProgramCohort cohort(LocalDate start, LocalDate end) {
        ProgramCohort cohort = new ProgramCohort();
        cohort.setId(9L);
        cohort.setAdmissionStart(start);
        cohort.setAdmissionEnd(end);
        return cohort;
    }

    private ProgramEnrollment enrollment(Long id, ProgramEnrollmentStatus status) {
        ProgramEnrollment enrollment = new ProgramEnrollment();
        enrollment.setId(id);
        enrollment.setStudent(student);
        enrollment.setProgram(program);
        enrollment.setStatus(status);
        enrollment.setEnrollmentDate(NOW.minusMonths(2));
        return enrollment;
    }

    private void givenStudentAndProgram() {
        when(studentFinder.requireByIdForUpdate(7L)).thenReturn(student);
        when(academicReferenceFinder.findProgram(4L)).thenReturn(program);
    }

    private void givenNoActiveEnrollment() {
        when(programEnrollmentRepository.findByStudentIdAndProgramIdAndStatusIn(7L, 4L,
                List.of(ProgramEnrollmentStatus.ENROLLED, ProgramEnrollmentStatus.PAUSED))).thenReturn(List.of());
        when(programEnrollmentRepository.save(any(ProgramEnrollment.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void enrollStopsWhenTheStudentHasOutstandingDebt() {
        doThrow(new IllegalStateException("debt")).when(enrollmentDebtGuard).verifyNoOutstandingDebt(7L);

        assertThatThrownBy(() -> service.enroll(7L, new CreateProgramEnrollmentRequest(4L))).isInstanceOf(IllegalStateException.class);
        verify(programEnrollmentRepository, never()).save(any());
    }

    @Test
    void enrollWithoutCohortCreatesAnEnrolledProgramEnrollmentAtClockTime() {
        givenStudentAndProgram();
        givenNoActiveEnrollment();

        ProgramEnrollment saved = service.enroll(7L, new CreateProgramEnrollmentRequest(4L));

        assertThat(saved.getStatus()).isEqualTo(ProgramEnrollmentStatus.ENROLLED);
        assertThat(saved.getEnrollmentDate()).isEqualTo(NOW);
        assertThat(saved.getProgramCohort()).isNull();
        verify(studentEnrollmentEligibility).requireConfirmedAndActive(student);
    }

    @Test
    void enrollWithCohortInsideTheAdmissionWindowLinksTheCohort() {
        givenStudentAndProgram();
        givenNoActiveEnrollment();
        ProgramCohort cohort = cohort(LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 2));
        when(programCohortRepository.findById(9L)).thenReturn(Optional.of(cohort));

        assertThat(service.enroll(7L, new CreateProgramEnrollmentRequest(4L, 9L)).getProgramCohort()).isSameAs(cohort);
    }

    @Test
    void enrollFailsWhenTheCohortDoesNotExist() {
        givenStudentAndProgram();
        when(programCohortRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.enroll(7L, new CreateProgramEnrollmentRequest(4L, 9L)))
                .isInstanceOf(ProgramCohortNotFoundException.class);
    }

    @Test
    void enrollFailsWhenTheCohortAdmissionWindowIsConfiguredBackwards() {
        givenStudentAndProgram();
        when(programCohortRepository.findById(9L)).thenReturn(Optional.of(cohort(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 3, 1))));

        assertThatThrownBy(() -> service.enroll(7L, new CreateProgramEnrollmentRequest(4L, 9L)))
                .isInstanceOf(InvalidWindowConfigurationException.class);
    }

    @Test
    void enrollFailsOutsideTheCohortAdmissionWindow() {
        givenStudentAndProgram();
        when(programCohortRepository.findById(9L)).thenReturn(Optional.of(cohort(LocalDate.of(2026, 3, 3), LocalDate.of(2026, 3, 30))));

        assertThatThrownBy(() -> service.enroll(7L, new CreateProgramEnrollmentRequest(4L, 9L)))
                .isInstanceOf(CohortAdmissionWindowClosedException.class);
        verify(programEnrollmentRepository, never()).save(any());
    }

    @Test
    void enrollAfterAdmissionPaymentSkipsTheAdmissionWindowAndEligibilityChecks() {
        givenStudentAndProgram();
        givenNoActiveEnrollment();
        ProgramCohort closedCohort = cohort(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));
        when(programCohortRepository.findById(9L)).thenReturn(Optional.of(closedCohort));

        ProgramEnrollment saved = service.enrollAfterAdmissionPayment(7L, new CreateProgramEnrollmentRequest(4L, 9L));

        assertThat(saved.getProgramCohort()).isSameAs(closedCohort);
        verify(studentEnrollmentEligibility, never()).requireConfirmedAndActive(any());
        verify(enrollmentDebtGuard, never()).verifyNoOutstandingDebt(any());
    }

    @Test
    void enrollFailsWhenTheStudentAlreadyHasAnEnrolledOrPausedEnrollmentInTheProgram() {
        givenStudentAndProgram();
        when(programEnrollmentRepository.findByStudentIdAndProgramIdAndStatusIn(7L, 4L,
                List.of(ProgramEnrollmentStatus.ENROLLED, ProgramEnrollmentStatus.PAUSED)))
                .thenReturn(List.of(enrollment(1L, ProgramEnrollmentStatus.PAUSED)));

        assertThatThrownBy(() -> service.enroll(7L, new CreateProgramEnrollmentRequest(4L)))
                .isInstanceOf(AlreadyEnrolledInProgramException.class);
        verify(programEnrollmentRepository, never()).save(any());
    }

    @Test
    void validateStudentCanEnrollRejectsAnActiveEnrollmentInTheSameProgram() {
        when(programEnrollmentRepository.findByStudentIdAndProgramIdAndStatusIn(7L, 4L,
                List.of(ProgramEnrollmentStatus.ENROLLED, ProgramEnrollmentStatus.PAUSED)))
                .thenReturn(List.of(enrollment(1L, ProgramEnrollmentStatus.ENROLLED)));

        assertThatThrownBy(() -> service.validateStudentCanEnrollInProgram(student, program))
                .isInstanceOf(AlreadyEnrolledInProgramException.class);
        verify(studentEnrollmentEligibility).requireConfirmedAndActive(student);
    }

    @Test
    void validateStudentCanEnrollAcceptsAStudentWithoutActiveEnrollment() {
        when(programEnrollmentRepository.findByStudentIdAndProgramIdAndStatusIn(7L, 4L,
                List.of(ProgramEnrollmentStatus.ENROLLED, ProgramEnrollmentStatus.PAUSED))).thenReturn(List.of());

        service.validateStudentCanEnrollInProgram(student, program);

        verify(studentEnrollmentEligibility).requireConfirmedAndActive(student);
    }

    @ParameterizedTest
    @EnumSource(value = ProgramEnrollmentStatus.class, names = {"ENROLLED", "PAUSED"})
    void withdrawRecordsTheWithdrawalAndReportsItToBilling(ProgramEnrollmentStatus status) {
        ProgramEnrollment active = enrollment(1L, status);
        when(programEnrollmentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(active));
        when(withdrawalDateResolver.resolveEffectiveDate(any(), any())).thenReturn(LocalDate.of(2026, 3, 2));
        when(withdrawalDateResolver.registrationDate()).thenReturn(LocalDate.of(2026, 3, 2));
        when(withdrawalDateResolver.completionMomentOf(LocalDate.of(2026, 3, 2))).thenReturn(NOW);

        service.withdrawForNonpayment(1L);

        assertThat(active.getStatus()).isEqualTo(ProgramEnrollmentStatus.WITHDRAWN);
        verify(programEnrollmentRepository).save(active);
        ArgumentCaptor<WithdrawnEnrollmentFacts> facts = ArgumentCaptor.forClass(WithdrawnEnrollmentFacts.class);
        verify(withdrawalBillingPort).registerWithdrawalForBilling(facts.capture());
        assertThat(facts.getValue().programEnrollmentId()).isEqualTo(1L);
        assertThat(facts.getValue().cause()).isEqualTo(WithdrawalCause.NONPAYMENT);
    }

    @Test
    void withdrawLocksTheEnrollmentBeforeNotifyingBilling() {
        ProgramEnrollment active = enrollment(1L, ProgramEnrollmentStatus.ENROLLED);
        when(programEnrollmentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(active));
        when(withdrawalDateResolver.resolveEffectiveDate(any(), any())).thenReturn(LocalDate.of(2026, 3, 2));
        when(withdrawalDateResolver.registrationDate()).thenReturn(LocalDate.of(2026, 3, 2));
        when(withdrawalDateResolver.completionMomentOf(LocalDate.of(2026, 3, 2))).thenReturn(NOW);

        service.withdrawForNonpayment(1L);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(programEnrollmentRepository, withdrawalBillingPort);
        order.verify(programEnrollmentRepository).findByIdForUpdate(1L);
        order.verify(withdrawalBillingPort).registerWithdrawalForBilling(any());
    }

    @ParameterizedTest
    @EnumSource(value = ProgramEnrollmentStatus.class, names = {"ENROLLED", "PAUSED"}, mode = EnumSource.Mode.EXCLUDE)
    void withdrawRejectsEnrollmentsThatAreNeitherEnrolledNorPaused(ProgramEnrollmentStatus status) {
        ProgramEnrollment inactive = enrollment(1L, status);
        when(programEnrollmentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.withdraw(1L, EnrollmentWithdrawal.forNonpayment())).isInstanceOf(CannotWithdrawException.class);
        assertThat(inactive.getStatus()).isEqualTo(status);
        verify(withdrawalBillingPort, never()).registerWithdrawalForBilling(any());
    }

    @Test
    void getAndListExposeProgramEnrollmentsAndFailForUnknownIds() {
        ProgramEnrollment existing = enrollment(1L, ProgramEnrollmentStatus.ENROLLED);
        when(programEnrollmentRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(programEnrollmentRepository.findById(2L)).thenReturn(Optional.empty());
        when(programEnrollmentRepository.findByStudentId(7L)).thenReturn(List.of(existing));

        assertThat(service.get(1L)).isSameAs(existing);
        assertThat(service.listByStudent(7L)).containsExactly(existing);
        assertThatThrownBy(() -> service.get(2L)).isInstanceOf(ProgramEnrollmentNotFoundException.class);
    }

    @Test
    void advanceAcademicYearAssignsTheNewBillingTemplateOfTheSameProgram() {
        ProgramEnrollment existing = enrollment(1L, ProgramEnrollmentStatus.ENROLLED);
        ProgramYearBillingTemplate template = new ProgramYearBillingTemplate();
        template.setId(20L);
        template.setProgram(program);
        when(programEnrollmentRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(templateRepository.findById(20L)).thenReturn(Optional.of(template));
        when(programEnrollmentRepository.save(existing)).thenReturn(existing);

        assertThat(service.advanceAcademicYear(1L, 20L).getCurrentBillingTemplate()).isSameAs(template);
    }

    @Test
    void advanceAcademicYearRejectsATemplateOfAnotherProgram() {
        ProgramEnrollment existing = enrollment(1L, ProgramEnrollmentStatus.ENROLLED);
        Program other = new Program();
        other.setId(5L);
        ProgramYearBillingTemplate template = new ProgramYearBillingTemplate();
        template.setId(20L);
        template.setProgram(other);
        when(programEnrollmentRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(templateRepository.findById(20L)).thenReturn(Optional.of(template));

        assertThatThrownBy(() -> service.advanceAcademicYear(1L, 20L)).isInstanceOf(ProgramYearBillingTemplateMismatchException.class);
        assertThat(existing.getCurrentBillingTemplate()).isNull();
        verify(programEnrollmentRepository, never()).save(any());
    }

    @Test
    void advanceAcademicYearFailsWhenTheTemplateDoesNotExist() {
        when(programEnrollmentRepository.findById(1L)).thenReturn(Optional.of(enrollment(1L, ProgramEnrollmentStatus.ENROLLED)));
        when(templateRepository.findById(20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.advanceAcademicYear(1L, 20L)).isInstanceOf(ProgramYearBillingTemplateNotFoundException.class);
    }

    @Test
    void progressIsMappedFromTheReaderIncludingElectiveGroupsAndAverage() {
        when(programProgressReader.calculateProgress(1L)).thenReturn(new ProgramProgressView(1L, 30, 120, List.of("Algebra"),
                List.of(new ElectiveGroupProgressView(3L, "Optativas", 1, 2, 3, false)), false, true, Grade.of(new BigDecimal("7.50"))));

        ProgramProgressResponse response = service.calculateProgress(1L);

        assertThat(response.creditosAcumulados()).isEqualTo(30);
        assertThat(response.creditosRequeridos()).isEqualTo(120);
        assertThat(response.obligatoriasPendientes()).containsExactly("Algebra");
        assertThat(response.gruposElectivos()).singleElement().satisfies(group -> {
            assertThat(group.electiveGroupName()).isEqualTo("Optativas");
            assertThat(group.cantidadAprobada()).isEqualTo(1);
        });
        assertThat(response.esRegular()).isTrue();
        assertThat(response.promedioGeneral()).isEqualByComparingTo("7.50");
    }

    @Test
    void progressWithoutAverageLeavesTheOverallAverageEmpty() {
        when(programProgressReader.calculateProgress(1L)).thenReturn(new ProgramProgressView(1L, 0, 120, List.of(), List.of(), false, false, null));

        assertThat(service.calculateProgress(1L).promedioGeneral()).isNull();
    }

    @Test
    void attendanceSummaryReportsPresenceAsTheComplementOfAbsenceAndNullWhenNotCalculable() {
        SubjectSectionEnrollment withAbsences = sectionEnrollment(100L, "Matematica");
        SubjectSectionEnrollment withoutData = sectionEnrollment(101L, "Historia");
        when(subjectSectionEnrollmentRepository.findWithSectionByProgramEnrollmentId(1L)).thenReturn(List.of(withAbsences, withoutData));
        when(attendanceRiskUseCase.readProgramEnrollmentRisks(List.of(100L, 101L))).thenReturn(java.util.Map.of(
                100L, new AttendanceRiskView(true, false, Percentage.ofFraction(new BigDecimal("0.25")), null),
                101L, new AttendanceRiskView(false, false, null, null)));

        List<SubjectAttendanceSummaryResponse> summary = service.attendanceSummaryBySubject(1L);

        assertThat(summary).extracting(SubjectAttendanceSummaryResponse::subjectName).containsExactly("Matematica", "Historia");
        assertThat(summary.get(0).attendancePercent()).isEqualTo(Percentage.ofFraction(new BigDecimal("0.75")));
        assertThat(summary.get(1).attendancePercent()).isNull();
    }

    private SubjectSectionEnrollment sectionEnrollment(Long id, String subjectName) {
        Subject subject = new Subject();
        subject.setName(subjectName);
        SubjectSection section = new SubjectSection();
        section.setSubject(subject);
        SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
        enrollment.setId(id);
        enrollment.setSubjectSection(section);
        return enrollment;
    }

    @Test
    void delegatingQueriesAskTheProgressReader() {
        when(programProgressReader.hasOnlyFinalExamsLeft(1L)).thenReturn(true);
        when(programProgressReader.hasActiveEnrollment(1L)).thenReturn(true);
        when(programProgressReader.listApprovedSubjects(1L)).thenReturn(List.of());

        assertThat(service.hasOnlyFinalExamsLeft(1L)).isTrue();
        assertThat(service.hasActiveEnrollment(1L)).isTrue();
        assertThat(service.listApprovedSubjects(1L)).isEmpty();
    }
}
