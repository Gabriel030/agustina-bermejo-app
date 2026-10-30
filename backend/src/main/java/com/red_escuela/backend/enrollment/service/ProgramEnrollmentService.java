package com.red_escuela.backend.enrollment.service;
import java.time.Clock;
import com.red_escuela.backend.shared.domain.Grade;
import com.red_escuela.backend.shared.domain.Percentage;
import java.math.BigDecimal;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.exception.StudentNotActiveException;
import com.red_escuela.backend.identity.exception.StudentNotConfirmedException;
import com.red_escuela.backend.identity.exception.StudentNotFoundException;
import com.red_escuela.backend.identity.repository.StudentRepository;

import com.red_escuela.backend.enrollment.dto.CreateProgramEnrollmentRequest;
import com.red_escuela.backend.enrollment.dto.ElectiveGroupProgressResponse;
import com.red_escuela.backend.enrollment.dto.ProgramProgressResponse;
import com.red_escuela.backend.enrollment.dto.SubjectAttendanceSummaryResponse;
import com.red_escuela.backend.academicstructure.api.AcademicReferenceFinder;
import com.red_escuela.backend.academicstructure.api.ElectiveGroupRequirementView;
import com.red_escuela.backend.academicstructure.api.ProgramCurriculumFinder;
import com.red_escuela.backend.academicstructure.api.ProgramSubjectView;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.enrollment.api.EnrollmentWithdrawalBillingPort;
import com.red_escuela.backend.enrollment.api.WithdrawnEnrollmentFacts;
import com.red_escuela.backend.billing.entity.ProgramCohort;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplication;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
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
import com.red_escuela.backend.billing.api.EnrollmentDebtGuard;
import com.red_escuela.backend.attendance.usecase.attendance.CalculateProgramEnrollmentAttendanceRiskUseCase;

@Service
public class ProgramEnrollmentService {
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final StudentRepository studentRepository;
    private final AcademicReferenceFinder academicReferenceFinder;
    private final ProgramCurriculumFinder programCurriculumFinder;
    private final SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository;
    private final ProgramCohortRepository programCohortRepository;
    private final ProgramYearBillingTemplateRepository programYearBillingTemplateRepository;
    private final CalculateProgramEnrollmentAttendanceRiskUseCase calculateProgramEnrollmentAttendanceRiskUseCase;
    private final EnrollmentDebtGuard enrollmentDebtGuard;
    private final EnrollmentWithdrawalDateResolver withdrawalDateResolver;
    private final EnrollmentWithdrawalBillingPort withdrawalBillingPort;
    private final Clock clock;

    public ProgramEnrollmentService(ProgramEnrollmentRepository programEnrollmentRepository,
            StudentRepository studentRepository,
            AcademicReferenceFinder academicReferenceFinder,
            ProgramCurriculumFinder programCurriculumFinder,
            SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository,
            ProgramCohortRepository programCohortRepository,
            ProgramYearBillingTemplateRepository programYearBillingTemplateRepository,
            CalculateProgramEnrollmentAttendanceRiskUseCase calculateProgramEnrollmentAttendanceRiskUseCase,
            EnrollmentDebtGuard enrollmentDebtGuard,
            EnrollmentWithdrawalDateResolver withdrawalDateResolver,
            EnrollmentWithdrawalBillingPort withdrawalBillingPort,
            Clock clock) {
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.studentRepository = studentRepository;
        this.academicReferenceFinder = academicReferenceFinder;
        this.programCurriculumFinder = programCurriculumFinder;
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
        Student student = studentRepository.findByIdForUpdate(studentId)
                .orElseThrow(() -> new StudentNotFoundException(studentId));
        if (validateEligibility) {
            validateStudentEligible(student);
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
        ProgramEnrollment enrollment = findOrThrow(id);
        Program program = enrollment.getProgram();

        List<ProgramSubjectView> entradas = programCurriculumFinder.findProgramSubjects(program.getId());
        List<ProgramSubjectView> aprobadas = listApprovedSubjects(id);

        List<String> obligatoriasPendientes = entradas.stream()
                .filter(entrada -> !entrada.isElective() && !aprobadas.contains(entrada))
                .map(entrada -> entrada.subjectName())
                .toList();

        int creditosAcumulados = aprobadas.stream().mapToInt(ProgramSubjectView::credits).sum();
        int creditosRequeridos = program.getTotalCreditsRequired();

        List<ElectiveGroupRequirementView> grupos = programCurriculumFinder.findElectiveGroupRequirements(program.getId());
        List<ElectiveGroupProgressResponse> gruposElectivos = grupos.stream()
                .map(grupo -> {
                    long cantidadAprobada = aprobadas.stream()
                            .filter(entrada -> entrada.isElective()
                                    && entrada.electiveGroupId().equals(grupo.electiveGroupId()))
                            .count();
                    boolean cumplido = cantidadAprobada >= grupo.minRequired();
                    return new ElectiveGroupProgressResponse(
                            grupo.electiveGroupId(), grupo.name(),
                            (int) cantidadAprobada, grupo.minRequired(), grupo.maxAllowed(), cumplido);
                })
                .toList();

        boolean studyPlanLoaded = !entradas.isEmpty();
        boolean elegibleParaCompletar = studyPlanLoaded
                && obligatoriasPendientes.isEmpty()
                && creditosAcumulados >= creditosRequeridos
                && gruposElectivos.stream().allMatch(ElectiveGroupProgressResponse::cumplido);

        List<Grade> finalGrades = subjectSectionEnrollmentRepository.findByProgramEnrollmentId(id).stream()
                .map(SubjectSectionEnrollment::getFinalScore)
                .filter(java.util.Objects::nonNull)
                .toList();
        BigDecimal promedioGeneral = Grade.average(finalGrades).map(Grade::value).orElse(null);

        return new ProgramProgressResponse(
                enrollment.getId(), creditosAcumulados, creditosRequeridos,
                obligatoriasPendientes, gruposElectivos, elegibleParaCompletar, hasActiveEnrollment(id), promedioGeneral);
    }

    public boolean hasOnlyFinalExamsLeft(Long id) {
        ProgramEnrollment enrollment = findOrThrow(id);
        Program program = enrollment.getProgram();
        List<ProgramSubjectView> entries = programCurriculumFinder.findProgramSubjects(program.getId());
        if (entries.isEmpty()
                || subjectSectionEnrollmentRepository.existsByProgramEnrollmentIdAndStatusIn(
                        id, List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS))) {
            return false;
        }
        List<ProgramSubjectView> coursed = entries.stream().filter(entry -> isCoursed(id, entry)).toList();
        boolean mandatoryPending = entries.stream().anyMatch(entry -> !entry.isElective() && !coursed.contains(entry));
        int coursedCredits = coursed.stream().mapToInt(ProgramSubjectView::credits).sum();
        boolean electiveGroupsMet = programCurriculumFinder.findElectiveGroupRequirements(program.getId()).stream()
                .allMatch(group -> coursed.stream()
                        .filter(entry -> entry.isElective()
                                && entry.electiveGroupId().equals(group.electiveGroupId()))
                        .count() >= group.minRequired());
        return !coursed.isEmpty() && program.getTotalCreditsRequired() > 0
                && !mandatoryPending && coursedCredits >= program.getTotalCreditsRequired() && electiveGroupsMet;
    }

    private boolean isCoursed(Long programEnrollmentId, ProgramSubjectView entry) {
        return !subjectSectionEnrollmentRepository
                .findByProgramEnrollmentIdAndSubjectSection_Subject_IdAndStatusIn(
                        programEnrollmentId, entry.subjectId(), List.of(EnrollmentStatus.APPROVED, EnrollmentStatus.REGULAR))
                .isEmpty();
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
        return subjectSectionEnrollmentRepository.existsByProgramEnrollmentIdAndStatusIn(
                programEnrollmentId,
                List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS, EnrollmentStatus.REGULAR));
    }

    public List<ProgramSubjectView> listApprovedSubjects(Long programEnrollmentId) {
        ProgramEnrollment enrollment = findOrThrow(programEnrollmentId);
        List<ProgramSubjectView> entradas = programCurriculumFinder.findProgramSubjects(enrollment.getProgram().getId());
        return entradas.stream()
                .filter(entrada -> isApproved(programEnrollmentId, entrada))
                .toList();
    }

    private boolean isApproved(Long programEnrollmentId, ProgramSubjectView entrada) {
        return !subjectSectionEnrollmentRepository
                .findByProgramEnrollmentIdAndSubjectSection_Subject_IdAndStatusIn(
                        programEnrollmentId, entrada.subjectId(), List.of(EnrollmentStatus.APPROVED))
                .isEmpty();
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
        validateStudentEligible(student);
        boolean alreadyEnrolled = !programEnrollmentRepository.findByStudentIdAndProgramIdAndStatusIn(
                student.getId(), program.getId(), List.of(ProgramEnrollmentStatus.ENROLLED, ProgramEnrollmentStatus.PAUSED)).isEmpty();
        if (alreadyEnrolled) {
            throw new AlreadyEnrolledInProgramException(student.getId(), program.getId());
        }
    }

    private void validateStudentEligible(Student student) {
        if (student.getOnboardingStatus() != StudentOnboardingStatus.CONFIRMADO) {
            throw new StudentNotConfirmedException(student.getId());
        }
        if (student.getPerson().getStatus() != StatusType.ACTIVE) {
            throw new StudentNotActiveException(student.getId());
        }
    }

    private ProgramEnrollment findOrThrow(Long id) {
        return programEnrollmentRepository.findById(id)
                .orElseThrow(() -> new ProgramEnrollmentNotFoundException(id));
    }
}
