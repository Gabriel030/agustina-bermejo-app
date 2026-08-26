package com.red_escuela.backend.service;

import com.red_escuela.backend.dto.CreateProgramEnrollmentRequest;
import com.red_escuela.backend.dto.ElectiveGroupProgressResponse;
import com.red_escuela.backend.dto.ProgramProgressResponse;
import com.red_escuela.backend.entity.Program;
import com.red_escuela.backend.entity.ProgramElectiveGroup;
import com.red_escuela.backend.entity.ProgramEnrollment;
import com.red_escuela.backend.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.entity.ProgramSubject;
import com.red_escuela.backend.entity.EnrollmentStatus;
import com.red_escuela.backend.entity.Student;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.entity.StudentOnboardingStatus;
import com.red_escuela.backend.exception.AdmissionWindowClosedException;
import com.red_escuela.backend.exception.AlreadyEnrolledInProgramException;
import com.red_escuela.backend.exception.CannotWithdrawException;
import com.red_escuela.backend.exception.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.exception.ProgramNotFoundException;
import com.red_escuela.backend.exception.StudentNotActiveException;
import com.red_escuela.backend.exception.StudentNotConfirmedException;
import com.red_escuela.backend.exception.StudentNotFoundException;
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

    public ProgramEnrollmentService(
            ProgramEnrollmentRepository programEnrollmentRepository,
            StudentRepository studentRepository,
            ProgramRepository programRepository,
            ProgramSubjectRepository programSubjectRepository,
            SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository,
            ProgramElectiveGroupRepository programElectiveGroupRepository) {
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.studentRepository = studentRepository;
        this.programRepository = programRepository;
        this.programSubjectRepository = programSubjectRepository;
        this.subjectSectionEnrollmentRepository = subjectSectionEnrollmentRepository;
        this.programElectiveGroupRepository = programElectiveGroupRepository;
    }

    @Transactional
    public ProgramEnrollment matricular(Long studentId, CreateProgramEnrollmentRequest request) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new StudentNotFoundException(studentId));
        validarStudentHabilitado(student);

        Program program = programRepository.findById(request.programId())
                .orElseThrow(() -> new ProgramNotFoundException(request.programId()));

        LocalDateTime now = LocalDateTime.now();
        if (program.getAdmissionStart() != null && now.isBefore(program.getAdmissionStart())
                || program.getAdmissionEnd() != null && now.isAfter(program.getAdmissionEnd())) {
            throw new AdmissionWindowClosedException(
                    "Fuera de la ventana de admisión de la carrera " + program.getId());
        }

        List<ProgramEnrollment> activas = programEnrollmentRepository.findByStudentIdAndProgramIdAndStatusIn(
                studentId, program.getId(), List.of(ProgramEnrollmentStatus.ENROLLED, ProgramEnrollmentStatus.PAUSED));
        if (!activas.isEmpty()) {
            throw new AlreadyEnrolledInProgramException(studentId, program.getId());
        }

        ProgramEnrollment enrollment = new ProgramEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgram(program);
        enrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
        enrollment.setEnrollmentDate(now);
        return programEnrollmentRepository.save(enrollment);
    }

    @Transactional
    public void darDeBaja(Long id) {
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

    public ProgramEnrollment verDetalle(Long id) {
        return findOrThrow(id);
    }

    public List<ProgramEnrollment> listarPorAlumno(Long studentId) {
        return programEnrollmentRepository.findByStudentId(studentId);
    }

    public ProgramProgressResponse calcularProgreso(Long id) {
        ProgramEnrollment enrollment = findOrThrow(id);
        Program program = enrollment.getProgram();

        List<ProgramSubject> entradas = programSubjectRepository.findByProgramId(program.getId());
        List<ProgramSubject> aprobadas = entradas.stream()
                .filter(entrada -> estaAprobada(enrollment.getId(), entrada))
                .toList();

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

        return new ProgramProgressResponse(
                enrollment.getId(), creditosAcumulados, creditosRequeridos,
                obligatoriasPendientes, gruposElectivos, elegibleParaCompletar);
    }

    private boolean estaAprobada(Long programEnrollmentId, ProgramSubject entrada) {
        return !subjectSectionEnrollmentRepository
                .findByProgramEnrollmentIdAndSubjectSection_Subject_IdAndStatusIn(
                        programEnrollmentId, entrada.getSubject().getId(), List.of(EnrollmentStatus.APPROVED))
                .isEmpty();
    }

    private void validarStudentHabilitado(Student student) {
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
