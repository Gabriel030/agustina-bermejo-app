package com.red_escuela.backend.enrollment.service;
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
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.billing.entity.ProgramCohort;
import com.red_escuela.backend.academicstructure.entity.ProgramElectiveGroup;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.academicstructure.entity.ProgramSubject;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.billing.exception.CohortAdmissionWindowClosedException;
import com.red_escuela.backend.enrollment.exception.AlreadyEnrolledInProgramException;
import com.red_escuela.backend.enrollment.exception.CannotWithdrawException;
import com.red_escuela.backend.enrollment.exception.InvalidWindowConfigurationException;
import com.red_escuela.backend.billing.exception.ProgramCohortNotFoundException;
import com.red_escuela.backend.enrollment.exception.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.academicstructure.exception.ProgramNotFoundException;
import com.red_escuela.backend.billing.exception.ProgramYearBillingTemplateMismatchException;
import com.red_escuela.backend.billing.exception.ProgramYearBillingTemplateNotFoundException;
import com.red_escuela.backend.billing.repository.ProgramCohortRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramElectiveGroupRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramSubjectRepository;
import com.red_escuela.backend.billing.repository.ProgramYearBillingTemplateRepository;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import com.red_escuela.backend.billing.service.EnrollmentWithdrawalCreditService;
import com.red_escuela.backend.billing.service.ScholarshipService;
import com.red_escuela.backend.billing.service.StudentDebtResolutionService;
import com.red_escuela.backend.attendance.usecase.attendance.CalculateProgramEnrollmentAttendanceRiskUseCase;

@Service
public class ProgramEnrollmentService {
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final StudentRepository studentRepository;
    private final ProgramRepository programRepository;
    private final ProgramSubjectRepository programSubjectRepository;
    private final SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository;
    private final ProgramElectiveGroupRepository programElectiveGroupRepository;
    private final ProgramCohortRepository programCohortRepository;
    private final ProgramYearBillingTemplateRepository programYearBillingTemplateRepository;
    private final CalculateProgramEnrollmentAttendanceRiskUseCase calculateProgramEnrollmentAttendanceRiskUseCase;
    private final StudentDebtResolutionService studentDebtResolutionService;
    private final ScholarshipService scholarshipService;
    private final EnrollmentWithdrawalCreditService enrollmentWithdrawalCreditService;

    public ProgramEnrollmentService(
            ProgramEnrollmentRepository programEnrollmentRepository,
            StudentRepository studentRepository,
            ProgramRepository programRepository,
            ProgramSubjectRepository programSubjectRepository,
            SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository,
            ProgramElectiveGroupRepository programElectiveGroupRepository,
            ProgramCohortRepository programCohortRepository,
            ProgramYearBillingTemplateRepository programYearBillingTemplateRepository,
            CalculateProgramEnrollmentAttendanceRiskUseCase calculateProgramEnrollmentAttendanceRiskUseCase,
            StudentDebtResolutionService studentDebtResolutionService,
            ScholarshipService scholarshipService, EnrollmentWithdrawalCreditService enrollmentWithdrawalCreditService) {
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.studentRepository = studentRepository;
        this.programRepository = programRepository;
        this.programSubjectRepository = programSubjectRepository;
        this.subjectSectionEnrollmentRepository = subjectSectionEnrollmentRepository;
        this.programElectiveGroupRepository = programElectiveGroupRepository;
        this.programCohortRepository = programCohortRepository;
        this.programYearBillingTemplateRepository = programYearBillingTemplateRepository;
        this.calculateProgramEnrollmentAttendanceRiskUseCase = calculateProgramEnrollmentAttendanceRiskUseCase;
        this.studentDebtResolutionService = studentDebtResolutionService;
        this.scholarshipService = scholarshipService;
        this.enrollmentWithdrawalCreditService = enrollmentWithdrawalCreditService;
    }

    @Transactional
    public ProgramEnrollment enroll(Long studentId, CreateProgramEnrollmentRequest request) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new StudentNotFoundException(studentId));
        validateStudentEligible(student);
        studentDebtResolutionService.verifyNoDebt(studentId);

        Program program = programRepository.findById(request.programId())
                .orElseThrow(() -> new ProgramNotFoundException(request.programId()));

        ProgramCohort cohort = null;
        if (request.cohortId() != null) {
            cohort = programCohortRepository.findById(request.cohortId())
                    .orElseThrow(() -> new ProgramCohortNotFoundException(request.cohortId()));
            validateAdmissionWindow(cohort);
        }

        LocalDateTime now = LocalDateTime.now();
        List<ProgramEnrollment> activas = programEnrollmentRepository.findByStudentIdAndProgramIdAndStatusIn(
                studentId, program.getId(), List.of(ProgramEnrollmentStatus.ENROLLED, ProgramEnrollmentStatus.PAUSED));
        if (!activas.isEmpty()) {
            throw new AlreadyEnrolledInProgramException(studentId, program.getId());
        }

        ProgramEnrollment enrollment = new ProgramEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgram(program);
        enrollment.setProgramCohort(cohort);
        enrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
        enrollment.setEnrollmentDate(now);
        return programEnrollmentRepository.save(enrollment);
    }

    @Transactional
    public void withdraw(Long id) {
        ProgramEnrollment enrollment = findOrThrow(id);
        if (enrollment.getStatus() != ProgramEnrollmentStatus.ENROLLED
                && enrollment.getStatus() != ProgramEnrollmentStatus.PAUSED) {
            throw new CannotWithdrawException(
                    "La matriculación " + id + " no está ENROLLED/PAUSED, no se puede dar de baja");
        }
        enrollment.setStatus(ProgramEnrollmentStatus.WITHDRAWN);
        programEnrollmentRepository.save(enrollment);
        scholarshipService.cancelActiveForProgram(id, "Enrollment dado de baja");
        enrollmentWithdrawalCreditService.handleProgramEnrollmentWithdrawal(enrollment);
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

        List<ProgramSubject> entradas = programSubjectRepository.findByProgramId(program.getId());
        List<ProgramSubject> aprobadas = listApprovedSubjects(id);

        List<String> obligatoriasPendientes = entradas.stream()
                .filter(entrada -> entrada.getElectiveGroup() == null && !aprobadas.contains(entrada))
                .map(entrada -> entrada.getSubject().getName())
                .toList();

        int creditosAcumulados = aprobadas.stream().mapToInt(ProgramSubject::getCredits).sum();
        int creditosRequeridos = program.getTotalCreditsRequired();

        List<ProgramElectiveGroup> grupos = programElectiveGroupRepository.findByProgramId(program.getId());
        List<ElectiveGroupProgressResponse> gruposElectivos = grupos.stream()
                .map(grupo -> {
                    long cantidadAprobada = aprobadas.stream()
                            .filter(entrada -> entrada.getElectiveGroup() != null
                                    && entrada.getElectiveGroup().getId().equals(grupo.getElectiveGroup().getId()))
                            .count();
                    boolean cumplido = cantidadAprobada >= grupo.getMinRequired();
                    return new ElectiveGroupProgressResponse(
                            grupo.getElectiveGroup().getId(), grupo.getElectiveGroup().getName(),
                            (int) cantidadAprobada, grupo.getMinRequired(), grupo.getMaxAllowed(), cumplido);
                })
                .toList();

        boolean elegibleParaCompletar = obligatoriasPendientes.isEmpty()
                && creditosAcumulados >= creditosRequeridos
                && gruposElectivos.stream().allMatch(ElectiveGroupProgressResponse::cumplido);

        var notasFinales = subjectSectionEnrollmentRepository.findByProgramEnrollmentId(id).stream()
                .map(SubjectSectionEnrollment::getFinalScore)
                .filter(java.util.Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average();
        Double promedioGeneral = notasFinales.isPresent() ? notasFinales.getAsDouble() : null;

        return new ProgramProgressResponse(
                enrollment.getId(), creditosAcumulados, creditosRequeridos,
                obligatoriasPendientes, gruposElectivos, elegibleParaCompletar, hasActiveEnrollment(id), promedioGeneral);
    }

    public List<SubjectAttendanceSummaryResponse> attendanceSummaryBySubject(Long programEnrollmentId) {
        return subjectSectionEnrollmentRepository.findByProgramEnrollmentId(programEnrollmentId).stream()
                .map(enrollment -> {
                    var riesgo = calculateProgramEnrollmentAttendanceRiskUseCase.calculate(enrollment.getId());
                    Double asistencia = riesgo.porcentajeAusentismo() == null ? null : 100 - riesgo.porcentajeAusentismo();
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

    public List<ProgramSubject> listApprovedSubjects(Long programEnrollmentId) {
        ProgramEnrollment enrollment = findOrThrow(programEnrollmentId);
        List<ProgramSubject> entradas = programSubjectRepository.findByProgramId(enrollment.getProgram().getId());
        return entradas.stream()
                .filter(entrada -> isApproved(programEnrollmentId, entrada))
                .toList();
    }

    private boolean isApproved(Long programEnrollmentId, ProgramSubject entrada) {
        return !subjectSectionEnrollmentRepository
                .findByProgramEnrollmentIdAndSubjectSection_Subject_IdAndStatusIn(
                        programEnrollmentId, entrada.getSubject().getId(), List.of(EnrollmentStatus.APPROVED))
                .isEmpty();
    }

    public void validateAdmissionWindow(ProgramCohort cohort) {
        if (cohort.getAdmissionStart().isAfter(cohort.getAdmissionEnd())) {
            throw new InvalidWindowConfigurationException(
                    "La cohorte " + cohort.getId() + " tiene la ventana de admisión mal configurada "
                            + "(admissionStart posterior a admissionEnd)");
        }
        if (!cohort.isAdmissionOpenOn(LocalDate.now())) {
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
