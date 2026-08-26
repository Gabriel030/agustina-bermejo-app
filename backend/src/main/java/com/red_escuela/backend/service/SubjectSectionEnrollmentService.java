package com.red_escuela.backend.service;

import com.red_escuela.backend.dto.ChangeSectionRequest;
import com.red_escuela.backend.dto.CreateSectionEnrollmentRequest;
import com.red_escuela.backend.entity.EnrollmentStatus;
import com.red_escuela.backend.entity.ProgramEnrollment;
import com.red_escuela.backend.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.entity.ProgramSubject;
import com.red_escuela.backend.entity.ProgramSubjectPrerequisite;
import com.red_escuela.backend.entity.RequirementType;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.entity.Student;
import com.red_escuela.backend.entity.StudentOnboardingStatus;
import com.red_escuela.backend.entity.SubjectSection;
import com.red_escuela.backend.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.entity.Term;
import com.red_escuela.backend.exception.AlreadyEnrolledInSubjectException;
import com.red_escuela.backend.exception.CannotWithdrawException;
import com.red_escuela.backend.exception.EnrollmentWindowClosedException;
import com.red_escuela.backend.exception.InvalidSectionChangeException;
import com.red_escuela.backend.exception.PrerequisiteNotMetException;
import com.red_escuela.backend.exception.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.exception.ProgramEnrollmentRequiredException;
import com.red_escuela.backend.exception.SectionEnrollmentNotFoundException;
import com.red_escuela.backend.exception.SectionNotFoundException;
import com.red_escuela.backend.exception.StudentNotActiveException;
import com.red_escuela.backend.exception.StudentNotConfirmedException;
import com.red_escuela.backend.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.repository.ProgramSubjectPrerequisiteRepository;
import com.red_escuela.backend.repository.ProgramSubjectRepository;
import com.red_escuela.backend.repository.SubjectSectionEnrollmentRepository;
import com.red_escuela.backend.repository.SubjectSectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Pieza central de la spec: inscripción a una comisión de materia de carrera, con
 * correlatividades scopeadas por ProgramEnrollment (decisión 7 de Estructura académica /
 * decisión 7 de esta spec), cupo con lista de espera FIFO (decisión 2) y cambio de
 * comisión como operación neutra que no incrementa attemptNumber (decisión 3).
 */
@Service
public class SubjectSectionEnrollmentService {

    private final SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository;
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final SubjectSectionRepository subjectSectionRepository;
    private final ProgramSubjectRepository programSubjectRepository;
    private final ProgramSubjectPrerequisiteRepository prerequisiteRepository;

    public SubjectSectionEnrollmentService(
            SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository,
            ProgramEnrollmentRepository programEnrollmentRepository,
            SubjectSectionRepository subjectSectionRepository,
            ProgramSubjectRepository programSubjectRepository,
            ProgramSubjectPrerequisiteRepository prerequisiteRepository) {
        this.subjectSectionEnrollmentRepository = subjectSectionEnrollmentRepository;
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.subjectSectionRepository = subjectSectionRepository;
        this.programSubjectRepository = programSubjectRepository;
        this.prerequisiteRepository = prerequisiteRepository;
    }

    @Transactional
    public SubjectSectionEnrollment inscribir(Long programEnrollmentId, CreateSectionEnrollmentRequest request) {
        ProgramEnrollment programEnrollment = findProgramEnrollmentOrThrow(programEnrollmentId);
        validarProgramEnrollmentActivo(programEnrollment);
        Student student = programEnrollment.getStudent();
        validarStudentHabilitado(student);

        SubjectSection section = subjectSectionRepository.findById(request.sectionId())
                .orElseThrow(() -> new SectionNotFoundException(request.sectionId()));
        validarVentanaDeInscripcion(section.getTerm());

        Long subjectId = section.getSubject().getId();
        List<SubjectSectionEnrollment> activasMismaMateria = subjectSectionEnrollmentRepository
                .findByProgramEnrollmentIdAndSubjectSection_Subject_IdAndStatusIn(
                        programEnrollmentId, subjectId,
                        List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS, EnrollmentStatus.WAITLISTED));
        if (!activasMismaMateria.isEmpty()) {
            throw new AlreadyEnrolledInSubjectException(programEnrollmentId, subjectId);
        }

        validarCorrelatividades(programEnrollment, section);

        Optional<SubjectSectionEnrollment> existente = subjectSectionEnrollmentRepository
                .findByStudentIdAndSubjectSectionId(student.getId(), section.getId());
        if (existente.isPresent()) {
            if (existente.get().getStatus() != EnrollmentStatus.WITHDRAWN) {
                throw new AlreadyEnrolledInSubjectException(programEnrollmentId, subjectId);
            }
            // Decisión 7: baja + reinscripción a la MISMA comisión reactiva la fila existente
            // (el unique constraint (student_id, program_section_id) no admite una fila nueva).
            SubjectSectionEnrollment reactivada = existente.get();
            reactivada.setStatus(calcularStatusPorCupo(section));
            reactivada.setDroppedAt(null);
            return subjectSectionEnrollmentRepository.save(reactivada);
        }

        int intentosPrevios = subjectSectionEnrollmentRepository
                .findByProgramEnrollmentIdAndSubjectSection_Subject_IdAndStatusIn(
                        programEnrollmentId, subjectId, List.of(EnrollmentStatus.FAILED, EnrollmentStatus.WITHDRAWN))
                .size();

        SubjectSectionEnrollment nueva = new SubjectSectionEnrollment();
        nueva.setStudent(student);
        nueva.setProgramEnrollment(programEnrollment);
        nueva.setSubjectSection(section);
        nueva.setStatus(calcularStatusPorCupo(section));
        nueva.setEnrolledAt(LocalDateTime.now());
        nueva.setAttemptNumber(intentosPrevios + 1);
        return subjectSectionEnrollmentRepository.save(nueva);
    }

    @Transactional
    public void darDeBaja(Long id) {
        SubjectSectionEnrollment enrollment = findOrThrow(id);
        if (enrollment.getStatus() != EnrollmentStatus.ENROLLED && enrollment.getStatus() != EnrollmentStatus.IN_PROGRESS) {
            throw new CannotWithdrawException(
                    "La inscripción " + id + " no está ENROLLED/IN_PROGRESS, no se puede dar de baja");
        }
        enrollment.setStatus(EnrollmentStatus.WITHDRAWN);
        enrollment.setDroppedAt(LocalDateTime.now());
        subjectSectionEnrollmentRepository.save(enrollment);
        promoverSiguienteEnListaDeEspera(enrollment.getSubjectSection().getId());
    }

    @Transactional
    public SubjectSectionEnrollment cambiarComision(Long id, ChangeSectionRequest request) {
        SubjectSectionEnrollment origen = findOrThrow(id);
        if (origen.getStatus() != EnrollmentStatus.ENROLLED && origen.getStatus() != EnrollmentStatus.IN_PROGRESS) {
            throw new CannotWithdrawException(
                    "La inscripción " + id + " no está ENROLLED/IN_PROGRESS, no se puede cambiar de comisión");
        }

        SubjectSection destino = subjectSectionRepository.findById(request.newSectionId())
                .orElseThrow(() -> new SectionNotFoundException(request.newSectionId()));
        if (!destino.getSubject().getId().equals(origen.getSubjectSection().getSubject().getId())) {
            throw new InvalidSectionChangeException(
                    "La comisión destino tiene que ser de la misma materia que la comisión origen");
        }
        // "Cambiar" a la comisión en la que ya está es un no-op: sin este corte, la baja+alta
        // de abajo dispara la promoción FIFO sobre la MISMA sección antes de reactivar la fila
        // origen, y el alumno pierde su propia plaza a manos de quien estaba en la lista de espera.
        if (destino.getId().equals(origen.getSubjectSection().getId())) {
            return origen;
        }
        validarVentanaDeInscripcion(destino.getTerm());

        Student student = origen.getStudent();
        ProgramEnrollment programEnrollment = origen.getProgramEnrollment();
        Integer attemptNumber = origen.getAttemptNumber();
        LocalDateTime now = LocalDateTime.now();

        // Baja de la fila origen: dispara promoción de lista de espera igual que cualquier
        // baja, pero SIN pasar por darDeBaja() para no recalcular attemptNumber (decisión 3).
        origen.setStatus(EnrollmentStatus.WITHDRAWN);
        origen.setDroppedAt(now);
        subjectSectionEnrollmentRepository.save(origen);
        promoverSiguienteEnListaDeEspera(origen.getSubjectSection().getId());

        Optional<SubjectSectionEnrollment> existenteDestino = subjectSectionEnrollmentRepository
                .findByStudentIdAndSubjectSectionId(student.getId(), destino.getId());
        if (existenteDestino.isPresent() && existenteDestino.get().getStatus() == EnrollmentStatus.WITHDRAWN) {
            SubjectSectionEnrollment reactivada = existenteDestino.get();
            reactivada.setStatus(calcularStatusPorCupo(destino));
            reactivada.setDroppedAt(null);
            return subjectSectionEnrollmentRepository.save(reactivada);
        }

        SubjectSectionEnrollment nueva = new SubjectSectionEnrollment();
        nueva.setStudent(student);
        nueva.setProgramEnrollment(programEnrollment);
        nueva.setSubjectSection(destino);
        nueva.setStatus(calcularStatusPorCupo(destino));
        nueva.setEnrolledAt(now);
        nueva.setAttemptNumber(attemptNumber);
        return subjectSectionEnrollmentRepository.save(nueva);
    }

    public List<SubjectSectionEnrollment> listarPorMatriculacion(Long programEnrollmentId) {
        return subjectSectionEnrollmentRepository.findByProgramEnrollmentId(programEnrollmentId);
    }

    // Autoservicio /me: abarca todas las carreras activas del alumno.
    public List<SubjectSectionEnrollment> listarPorAlumno(Long studentId) {
        return subjectSectionEnrollmentRepository.findByStudentId(studentId);
    }

    public SubjectSectionEnrollment verDetalle(Long id) {
        return findOrThrow(id);
    }

    private void promoverSiguienteEnListaDeEspera(Long sectionId) {
        subjectSectionEnrollmentRepository
                .findFirstBySubjectSectionIdAndStatusOrderByEnrolledAtAsc(sectionId, EnrollmentStatus.WAITLISTED)
                .ifPresent(siguiente -> {
                    siguiente.setStatus(EnrollmentStatus.ENROLLED);
                    subjectSectionEnrollmentRepository.save(siguiente);
                });
    }

    private EnrollmentStatus calcularStatusPorCupo(SubjectSection section) {
        int ocupadas = subjectSectionEnrollmentRepository
                .findBySubjectSectionIdAndStatusIn(section.getId(),
                        List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS, EnrollmentStatus.REGULAR))
                .size();
        return section.getCapacity() == null || ocupadas < section.getCapacity()
                ? EnrollmentStatus.ENROLLED : EnrollmentStatus.WAITLISTED;
    }

    /**
     * Scopeada a la misma ProgramEnrollment (misma carrera): si el Subject se comparte
     * entre carreras, aprobarlo bajo otra carrera no cuenta acá (Spec Inscripciones,
     * sección 6 — coherente con la decisión 16 de Estructura académica).
     */
    private void validarCorrelatividades(ProgramEnrollment programEnrollment, SubjectSection section) {
        ProgramSubject programSubject = programSubjectRepository
                .findByProgramIdAndSubjectId(programEnrollment.getProgram().getId(), section.getSubject().getId())
                .orElseThrow(() -> new PrerequisiteNotMetException(
                        "La materia " + section.getSubject().getId() + " no está en el plan de la carrera "
                                + programEnrollment.getProgram().getId()));

        for (ProgramSubjectPrerequisite prereq : prerequisiteRepository.findByProgramSubjectId(programSubject.getId())) {
            List<EnrollmentStatus> estadosQueSirven = prereq.getRequirementType() == RequirementType.APPROVED
                    ? List.of(EnrollmentStatus.APPROVED)
                    : List.of(EnrollmentStatus.REGULAR, EnrollmentStatus.APPROVED);

            boolean cumplida = !subjectSectionEnrollmentRepository
                    .findByProgramEnrollmentIdAndSubjectSection_Subject_IdAndStatusIn(
                            programEnrollment.getId(), prereq.getPrerequisiteProgramSubject().getSubject().getId(),
                            estadosQueSirven)
                    .isEmpty();
            if (!cumplida) {
                throw new PrerequisiteNotMetException(
                        "No se cumple la correlatividad: " + prereq.getPrerequisiteProgramSubject().getSubject().getName());
            }
        }
    }

    private void validarProgramEnrollmentActivo(ProgramEnrollment programEnrollment) {
        if (programEnrollment.getStatus() != ProgramEnrollmentStatus.ENROLLED
                && programEnrollment.getStatus() != ProgramEnrollmentStatus.PAUSED) {
            throw new ProgramEnrollmentRequiredException(programEnrollment.getId());
        }
    }

    private void validarVentanaDeInscripcion(Term term) {
        LocalDateTime now = LocalDateTime.now();
        if (term.getEnrollmentStart() != null && now.isBefore(term.getEnrollmentStart())
                || term.getEnrollmentEnd() != null && now.isAfter(term.getEnrollmentEnd())) {
            throw new EnrollmentWindowClosedException("Fuera de la ventana de inscripción del Term " + term.getId());
        }
    }

    private void validarStudentHabilitado(Student student) {
        if (student.getOnboardingStatus() != StudentOnboardingStatus.CONFIRMADO) {
            throw new StudentNotConfirmedException(student.getId());
        }
        if (student.getPerson().getStatus() != StatusType.ACTIVE) {
            throw new StudentNotActiveException(student.getId());
        }
    }

    private ProgramEnrollment findProgramEnrollmentOrThrow(Long id) {
        return programEnrollmentRepository.findById(id)
                .orElseThrow(() -> new ProgramEnrollmentNotFoundException(id));
    }

    private SubjectSectionEnrollment findOrThrow(Long id) {
        return subjectSectionEnrollmentRepository.findById(id)
                .orElseThrow(() -> new SectionEnrollmentNotFoundException(id));
    }
}
