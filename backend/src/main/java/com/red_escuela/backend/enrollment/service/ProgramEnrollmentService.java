package com.red_escuela.backend.enrollment.service;
import java.time.Clock;
import com.red_escuela.backend.shared.domain.Grade;
import com.red_escuela.backend.shared.domain.Percentage;
import java.math.BigDecimal;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.api.StudentEnrollmentEligibility;
import com.red_escuela.backend.identity.api.StudentFinder;

import com.red_escuela.backend.enrollment.dto.CreateProgramEnrollmentRequest;
import com.red_escuela.backend.enrollment.dto.ElectiveGroupProgressResponse;
import com.red_escuela.backend.enrollment.dto.ProgramProgressResponse;
import com.red_escuela.backend.enrollment.dto.SubjectAttendanceSummaryResponse;
import com.red_escuela.backend.academicstructure.api.AcademicReferenceFinder;
import com.red_escuela.backend.academicstructure.api.ProgramSubjectView;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.enrollment.api.EnrollmentWithdrawalBillingPort;
import com.red_escuela.backend.enrollment.api.ProgramProgressReader;
import com.red_escuela.backend.enrollment.api.ProgramProgressView;
import com.red_escuela.backend.enrollment.api.WithdrawnEnrollmentFacts;
import com.red_escuela.backend.billing.entity.ProgramCohort;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplication;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.exception.CohortAdmissionWindowClosedException;
import com.red_escuela.backend.enrollment.exception.AlreadyEnrolledInProgramException;
import com.red_escuela.backend.enrollment.exception.CannotWithdrawException;
import com.red_escuela.backend.enrollment.exception.InvalidWindowConfigurationException;
import com.red_escuela.backend.billing.exception.ProgramCohortNotFoundException;
import com.red_escuela.backend.enrollment.exception.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.billing.exception.ProgramYearBillingTemplateMismatchException;
import com.red_escuela.backend.billing.exception.ProgramYearBillingTemplateNotFoundException;
import com.red_escuela.backend.billing.repository.ProgramCohortRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.billing.repository.ProgramYearBillingTemplateRepository;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import com.red_escuela.backend.billing.api.EnrollmentDebtGuard;
import com.red_escuela.backend.attendance.usecase.attendance.CalculateProgramEnrollmentAttendanceRiskUseCase;

@Service
public class ProgramEnrollmentService {
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final StudentFinder studentFinder;
    private final StudentEnrollmentEligibility studentEnrollmentEligibility;
    private final AcademicReferenceFinder academicReferenceFinder;
    private final ProgramProgressReader programProgressReader;
    private final SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository;
    private final ProgramCohortRepository programCohortRepository;
    private final ProgramYearBillingTemplateRepository programYearBillingTemplateRepository;
    private final CalculateProgramEnrollmentAttendanceRiskUseCase calculateProgramEnrollmentAttendanceRiskUseCase;
    private final EnrollmentDebtGuard enrollmentDebtGuard;
    private final EnrollmentWithdrawalDateResolver withdrawalDateResolver;
    private final EnrollmentWithdrawalBillingPort withdrawalBillingPort;
    private final Clock clock;

    public ProgramEnrollmentService(ProgramEnrollmentRepository programEnrollmentRepository,
            StudentFinder studentFinder,
            StudentEnrollmentEligibility studentEnrollmentEligibility,
            AcademicReferenceFinder academicReferenceFinder,
            ProgramProgressReader programProgressReader,
            SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository,
            ProgramCohortRepository programCohortRepository,
            ProgramYearBillingTemplateRepository programYearBillingTemplateRepository,
            CalculateProgramEnrollmentAttendanceRiskUseCase calculateProgramEnrollmentAttendanceRiskUseCase,
            EnrollmentDebtGuard enrollmentDebtGuard,
            EnrollmentWithdrawalDateResolver withdrawalDateResolver,
            EnrollmentWithdrawalBillingPort withdrawalBillingPort,
            Clock clock) {
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.studentFinder = studentFinder;
        this.studentEnrollmentEligibility = studentEnrollmentEligibility;
        this.academicReferenceFinder = academicReferenceFinder;
        this.programProgressReader = programProgressReader;
        this.subjectSectionEnrollmentRepository = subjectSectionEnrollmentRepository;
        this.programCohortRepository = programCohortRepository;
        this.programYearBillingTemplateRepository = programYearBillingTemplateRepository;
        this.calculateProgramEnrollmentAttendanceRiskUseCase = calculateProgramEnrollmentAttendanceRiskUseCase;
        this.enrollmentDebtGuard = enrollmentDebtGuard;
        this.withdrawalDateResolver = withdrawalDateResolver;
        this.withdrawalBillingPort = withdrawalBillingPort;
        this.clock = clock;
    }

    @Transactional
    public ProgramEnrollment enroll(Long studentId, CreateProgramEnrollmentRequest request) {
        verifyStudentHasNoOutstandingDebt(studentId);
        return createEnrollment(studentId, request, true, true, null);
    }

    @Transactional
    public ProgramEnrollment enrollAfterAdmissionPayment(Long studentId, CreateProgramEnrollmentRequest request) {
        return createEnrollment(studentId, request, false, false, null);
    }

    @Transactional
    public ProgramEnrollment enrollApplicantAfterAdmissionPayment(ProgramEnrollmentApplication application) {
        Long cohortId = application.getProgramCohort() == null ? null : application.getProgramCohort().getId();
        return createEnrollment(application.getStudent().getId(),
                new CreateProgramEnrollmentRequest(application.getProgram().getId(), cohortId), false, false, application);
    }

    private ProgramEnrollment createEnrollment(Long studentId, CreateProgramEnrollmentRequest request, boolean validateAdmissionWindow,
                                               boolean validateEligibility, ProgramEnrollmentApplication admissionApplication) {
        Student student = studentFinder.requireByIdForUpdate(studentId);
        if (validateEligibility) {
            studentEnrollmentEligibility.requireConfirmedAndActive(student);
        }

        Program program = academicReferenceFinder.findProgram(request.programId());

        ProgramCohort cohort = null;
        if (request.cohortId() != null) {
            cohort = programCohortRepository.findById(request.cohortId())
                    .orElseThrow(() -> new ProgramCohortNotFoundException(request.cohortId()));
            if (validateAdmissionWindow) {
                validateAdmissionWindow(cohort);
            }
        }

        LocalDateTime now = LocalDateTime.now(clock);
        List<ProgramEnrollment> activas = programEnrollmentRepository.findByStudentIdAndProgramIdAndStatusIn(
                studentId, program.getId(), List.of(ProgramEnrollmentStatus.ENROLLED, ProgramEnrollmentStatus.PAUSED));
        if (!activas.isEmpty()) {
            throw new AlreadyEnrolledInProgramException(studentId, program.getId());
        }

        ProgramEnrollment enrollment = new ProgramEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgram(program);
        enrollment.setProgramCohort(cohort);
        enrollment.setAdmissionApplication(admissionApplication);
        enrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
        enrollment.setEnrollmentDate(now);
        return programEnrollmentRepository.save(enrollment);
    }

    @Transactional
    public void withdrawForNonpayment(Long id) {
        withdraw(id, EnrollmentWithdrawal.forNonpayment());
    }

    @Transactional
    public void withdraw(Long id, EnrollmentWithdrawal withdrawal) {
        ProgramEnrollment enrollment = findOrThrow(id);
        if (enrollment.getStatus() != ProgramEnrollmentStatus.ENROLLED
                && enrollment.getStatus() != ProgramEnrollmentStatus.PAUSED) {
            throw new CannotWithdrawException(
                    "La matriculación " + id + " no está ENROLLED/PAUSED, no se puede dar de baja");
        }
        LocalDate effectiveDate = withdrawalDateResolver.resolveEffectiveDate(withdrawal, enrollment.getEnrollmentDate());
        LocalDate registeredOn = withdrawalDateResolver.registrationDate();
        enrollment.markWithdrawn(effectiveDate, withdrawalDateResolver.completionMomentOf(effectiveDate), registeredOn,
                withdrawal.registeredByPersonId());
        programEnrollmentRepository.save(enrollment);
        withdrawalBillingPort.registerWithdrawalForBilling(WithdrawnEnrollmentFacts.ofProgramEnrollment(
                enrollment.getStudent().getId(), id, effectiveDate, registeredOn, withdrawal.cause()));
    }

    public ProgramEnrollment get(Long id) {
        return findOrThrow(id);
    }

    public List<ProgramEnrollment> listByStudent(Long studentId) {
        return programEnrollmentRepository.findByStudentId(studentId);
    }

    public ProgramProgressResponse calculateProgress(Long id) {
        ProgramProgressView progress = programProgressReader.calculateProgress(id);
        List<ElectiveGroupProgressResponse> electiveGroups = progress.electiveGroups().stream()
                .map(group -> new ElectiveGroupProgressResponse(
                        group.electiveGroupId(), group.electiveGroupName(),
                        group.approvedCount(), group.minRequired(), group.maxAllowed(), group.fulfilled()))
                .toList();
        return new ProgramProgressResponse(
                progress.programEnrollmentId(), progress.accumulatedCredits(), progress.requiredCredits(),
                progress.pendingMandatorySubjects(), electiveGroups, progress.eligibleToComplete(),
                progress.regular(), Optional.ofNullable(progress.overallAverage()).map(Grade::value).orElse(null));
    }

    public boolean hasOnlyFinalExamsLeft(Long id) {
        return programProgressReader.hasOnlyFinalExamsLeft(id);
    }

    public List<SubjectAttendanceSummaryResponse> attendanceSummaryBySubject(Long programEnrollmentId) {
        return subjectSectionEnrollmentRepository.findByProgramEnrollmentId(programEnrollmentId).stream()
                .map(enrollment -> {
                    var riesgo = calculateProgramEnrollmentAttendanceRiskUseCase.calculate(enrollment.getId());
                    Percentage asistencia = riesgo.absencePercent() == null ? null
                            : Percentage.ofFraction(BigDecimal.ONE.subtract(riesgo.absencePercent().fraction()));
                    return new SubjectAttendanceSummaryResponse(
                            enrollment.getSubjectSection().getSubject().getName(), asistencia);
                })
                .toList();
    }

    public boolean hasActiveEnrollment(Long programEnrollmentId) {
        return programProgressReader.hasActiveEnrollment(programEnrollmentId);
    }

    public List<ProgramSubjectView> listApprovedSubjects(Long programEnrollmentId) {
        return programProgressReader.listApprovedSubjects(programEnrollmentId);
    }

    public void validateAdmissionWindow(ProgramCohort cohort) {
        if (cohort.getAdmissionStart().isAfter(cohort.getAdmissionEnd())) {
            throw new InvalidWindowConfigurationException(
                    "La cohorte " + cohort.getId() + " tiene la ventana de admisión mal configurada "
                            + "(admissionStart posterior a admissionEnd)");
        }
        if (!cohort.isAdmissionOpenOn(LocalDate.now(clock))) {
            throw new CohortAdmissionWindowClosedException(cohort.getId());
        }
    }

    @Transactional
    public ProgramEnrollment advanceAcademicYear(Long programEnrollmentId, Long newTemplateId) {
        ProgramEnrollment enrollment = findOrThrow(programEnrollmentId);
        ProgramYearBillingTemplate template = programYearBillingTemplateRepository.findById(newTemplateId)
                .orElseThrow(() -> new ProgramYearBillingTemplateNotFoundException(newTemplateId));
        if (!template.getProgram().getId().equals(enrollment.getProgram().getId())) {
            throw new ProgramYearBillingTemplateMismatchException(newTemplateId);
        }
        enrollment.setCurrentBillingTemplate(template);
        return programEnrollmentRepository.save(enrollment);
    }

    public void verifyStudentHasNoOutstandingDebt(Long studentId) {
        enrollmentDebtGuard.verifyNoOutstandingDebt(studentId);
    }

    public void validateStudentCanEnrollInProgram(Student student, Program program) {
        studentEnrollmentEligibility.requireConfirmedAndActive(student);
        boolean alreadyEnrolled = !programEnrollmentRepository.findByStudentIdAndProgramIdAndStatusIn(
                student.getId(), program.getId(), List.of(ProgramEnrollmentStatus.ENROLLED, ProgramEnrollmentStatus.PAUSED)).isEmpty();
        if (alreadyEnrolled) {
            throw new AlreadyEnrolledInProgramException(student.getId(), program.getId());
        }
    }

    private ProgramEnrollment findOrThrow(Long id) {
        return programEnrollmentRepository.findById(id)
                .orElseThrow(() -> new ProgramEnrollmentNotFoundException(id));
    }
}
