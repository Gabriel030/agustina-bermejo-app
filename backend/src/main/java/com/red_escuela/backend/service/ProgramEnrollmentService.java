package com.red_escuela.backend.service;

import com.red_escuela.backend.dto.CreateProgramEnrollmentRequest;
import com.red_escuela.backend.dto.ElectiveGroupProgressResponse;
import com.red_escuela.backend.dto.ProgramProgressResponse;
import com.red_escuela.backend.dto.SubjectAttendanceSummaryResponse;
import com.red_escuela.backend.entity.Program;
import com.red_escuela.backend.entity.ProgramCohort;
import com.red_escuela.backend.entity.ProgramElectiveGroup;
import com.red_escuela.backend.entity.ProgramEnrollment;
import com.red_escuela.backend.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.entity.ProgramSubject;
import com.red_escuela.backend.entity.EnrollmentStatus;
import com.red_escuela.backend.entity.Student;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.entity.StudentOnboardingStatus;
import com.red_escuela.backend.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.exception.AdmissionWindowClosedException;
import com.red_escuela.backend.exception.AlreadyEnrolledInProgramException;
import com.red_escuela.backend.exception.CannotWithdrawException;
import com.red_escuela.backend.exception.InvalidWindowConfigurationException;
import com.red_escuela.backend.exception.ProgramCohortNotFoundException;
import com.red_escuela.backend.exception.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.exception.ProgramNotFoundException;
import com.red_escuela.backend.exception.StudentNotActiveException;
import com.red_escuela.backend.exception.StudentNotConfirmedException;
import com.red_escuela.backend.exception.StudentNotFoundException;
import com.red_escuela.backend.repository.ProgramCohortRepository;
import com.red_escuela.backend.repository.ProgramElectiveGroupRepository;
import com.red_escuela.backend.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.repository.ProgramRepository;
import com.red_escuela.backend.repository.ProgramSubjectRepository;
import com.red_escuela.backend.repository.StudentRepository;
import com.red_escuela.backend.repository.SubjectSectionEnrollmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * RF-PR-02: alta/baja de matriculación a Program (carrera), exclusiva de Preceptor/
 * Administrativo (Spec Inscripciones académicas, decisión 12). Un alumno puede tener
 * varias carreras activas a la vez (decisión 1); lo que no se permite es duplicar la
 * matriculación a la MISMA carrera mientras la anterior sigue activa (decisión 9).
 */
@Service
public class ProgramEnrollmentService {

    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final StudentRepository studentRepository;
    private final ProgramRepository programRepository;
    private final ProgramSubjectRepository programSubjectRepository;
    private final SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository;
    private final ProgramElectiveGroupRepository programElectiveGroupRepository;
    private final ProgramCohortRepository programCohortRepository;
    private final AttendanceService attendanceService;

    public ProgramEnrollmentService(
            ProgramEnrollmentRepository programEnrollmentRepository,
            StudentRepository studentRepository,
            ProgramRepository programRepository,
            ProgramSubjectRepository programSubjectRepository,
            SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository,
            ProgramElectiveGroupRepository programElectiveGroupRepository,
            ProgramCohortRepository programCohortRepository,
            AttendanceService attendanceService) {
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.studentRepository = studentRepository;
        this.programRepository = programRepository;
        this.programSubjectRepository = programSubjectRepository;
        this.subjectSectionEnrollmentRepository = subjectSectionEnrollmentRepository;
        this.programElectiveGroupRepository = programElectiveGroupRepository;
        this.programCohortRepository = programCohortRepository;
        this.attendanceService = attendanceService;
    }

    @Transactional
    public ProgramEnrollment enroll(Long studentId, CreateProgramEnrollmentRequest request) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new StudentNotFoundException(studentId));
        validateStudentEligible(student);

        Program program = programRepository.findById(request.programId())
                .orElseThrow(() -> new ProgramNotFoundException(request.programId()));

        // cohortId nullable (ver CreateProgramEnrollmentRequest): un alta directa
        // por staff puede no especificar cohorte, y en ese caso no queda atada a
        // ninguna ventana de admisión de autoservicio — es una decisión explícita
        // del staff, no algo que el sistema deba bloquear.
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
        // No se da de baja en cascada las SubjectSectionEnrollment asociadas (Spec, sección 6):
        // una baja de carrera no borra el historial de cursadas del alumno.
        enrollment.setStatus(ProgramEnrollmentStatus.WITHDRAWN);
        programEnrollmentRepository.save(enrollment);
    }

    public ProgramEnrollment viewDetail(Long id) {
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

        // esRegular reusa tieneCursadaActiva (Épica 2 Fase 1, Alumno · Mis carreras) — mismo
        // criterio que ya usaba CertificateService para la constancia de alumno regular, ahora
        // también expuesto acá para mostrarlo como condición en la pantalla.
        return new ProgramProgressResponse(
                enrollment.getId(), creditosAcumulados, creditosRequeridos,
                obligatoriasPendientes, gruposElectivos, elegibleParaCompletar, hasActiveEnrollment(id), promedioGeneral);
    }

    // Épica 3 Fase 4: resumen de asistencia por materia para la vista de detalle de egreso del
    // Director — reusa el cálculo de riesgo de libre ya existente por cursada, agregándolo acá
    // en vez de duplicar la lógica de asistencia.
    public List<SubjectAttendanceSummaryResponse> attendanceSummaryBySubject(Long programEnrollmentId) {
        return subjectSectionEnrollmentRepository.findByProgramEnrollmentId(programEnrollmentId).stream()
                .map(enrollment -> {
                    var riesgo = attendanceService.calculateDropoutRiskByProgramEnrollment(enrollment.getId());
                    Double asistencia = riesgo.calculable() ? 100 - riesgo.porcentajeAusentismo() : null;
                    return new SubjectAttendanceSummaryResponse(
                            enrollment.getSubjectSection().getSubject().getName(), asistencia);
                })
                .toList();
    }

    /**
     * Reusado por CertificateService (constancia de alumno regular, ajuste de producto sobre la
     * spec original): además de ProgramEnrollmentStatus.ENROLLED, exige tener una cursada
     * puntual activa ahora mismo, no solo la matriculación a la carrera vigente.
     */
    public boolean hasActiveEnrollment(Long programEnrollmentId) {
        return subjectSectionEnrollmentRepository.existsByProgramEnrollmentIdAndStatusIn(
                programEnrollmentId,
                List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS, EnrollmentStatus.REGULAR));
    }

    /**
     * Reusado por CertificateService (constancia de materias aprobadas, Spec Procesos
     * administrativos): mismo criterio de "aprobada" que calculateProgress, sin duplicarlo.
     */
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

    // Package-private (no private): ProgramEnrollmentApplicationService.solicitarConPago la
    // reusa para no cobrarle matrícula a nadie fuera de la ventana de admisión — antes solo se
    // validaba acá, en enroll(), que recién corre cuando el Preceptor aprueba la solicitud
    // (tarde: el comprobante ya se había pedido y subido). Mismo criterio que
    // CourseEnrollmentService.validateCanEnroll. Desde Épica 8 Fase 2 la ventana vive en
    // ProgramCohort, no en Program (una carrera tiene 2 cohortes por año, cada una con la suya).
    void validateAdmissionWindow(ProgramCohort cohort) {
        if (cohort.getAdmissionStart() != null && cohort.getAdmissionEnd() != null
                && cohort.getAdmissionStart().isAfter(cohort.getAdmissionEnd())) {
            throw new InvalidWindowConfigurationException(
                    "La cohorte " + cohort.getId() + " tiene la ventana de admisión mal configurada "
                            + "(admissionStart posterior a admissionEnd)");
        }
        LocalDateTime now = LocalDateTime.now();
        if (cohort.getAdmissionStart() != null && now.isBefore(cohort.getAdmissionStart())
                || cohort.getAdmissionEnd() != null && now.isAfter(cohort.getAdmissionEnd())) {
            throw new AdmissionWindowClosedException(
                    "Fuera de la ventana de admisión de la cohorte " + cohort.getId());
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
