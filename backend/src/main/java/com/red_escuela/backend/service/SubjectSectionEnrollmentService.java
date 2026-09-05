package com.red_escuela.backend.service;

import com.red_escuela.backend.dto.ChangeSectionRequest;
import com.red_escuela.backend.dto.CreateSectionEnrollmentRequest;
import com.red_escuela.backend.entity.EnrollmentStatus;
import com.red_escuela.backend.entity.ProgramEnrollment;
import com.red_escuela.backend.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.entity.CurricularTerm;
import com.red_escuela.backend.entity.ProgramSubject;
import com.red_escuela.backend.entity.ProgramSubjectPrerequisite;
import com.red_escuela.backend.entity.RequirementType;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.entity.Student;
import com.red_escuela.backend.entity.StudentOnboardingStatus;
import com.red_escuela.backend.entity.SubjectSection;
import com.red_escuela.backend.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.entity.Term;
import com.red_escuela.backend.entity.TermEnrollmentOverride;
import com.red_escuela.backend.exception.AlreadyEnrolledInSubjectException;
import com.red_escuela.backend.exception.CannotWithdrawException;
import com.red_escuela.backend.exception.EnrollmentWindowClosedException;
import com.red_escuela.backend.exception.InvalidSectionChangeException;
import com.red_escuela.backend.exception.InvalidWindowConfigurationException;
import com.red_escuela.backend.exception.PrerequisiteNotMetException;
import com.red_escuela.backend.exception.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.exception.ProgramEnrollmentRequiredException;
import com.red_escuela.backend.exception.SectionEnrollmentNotFoundException;
import com.red_escuela.backend.exception.SectionNotFoundException;
import com.red_escuela.backend.exception.StudentNotActiveException;
import com.red_escuela.backend.exception.StudentNotConfirmedException;
import com.red_escuela.backend.exception.SubjectNotInProgramPlanException;
import com.red_escuela.backend.exception.TermEnrollmentOverrideClosedException;
import com.red_escuela.backend.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.repository.ProgramSubjectPrerequisiteRepository;
import com.red_escuela.backend.repository.ProgramSubjectRepository;
import com.red_escuela.backend.repository.SubjectSectionEnrollmentRepository;
import com.red_escuela.backend.repository.SubjectSectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
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
    public SubjectSectionEnrollment enroll(Long programEnrollmentId, CreateSectionEnrollmentRequest request) {
        ProgramEnrollment programEnrollment = findProgramEnrollmentOrThrow(programEnrollmentId);
        validateActiveProgramEnrollment(programEnrollment);
        Student student = programEnrollment.getStudent();
        validateStudentEligible(student);

        SubjectSection section = subjectSectionRepository.findById(request.sectionId())
                .orElseThrow(() -> new SectionNotFoundException(request.sectionId()));
        validateEnrollmentWindow(section.getTerm());

        Long subjectId = section.getSubject().getId();
        List<SubjectSectionEnrollment> activeSameSubject = subjectSectionEnrollmentRepository
                .findByProgramEnrollmentIdAndSubjectSection_Subject_IdAndStatusIn(
                        programEnrollmentId, subjectId,
                        List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS, EnrollmentStatus.WAITLISTED));
        if (!activeSameSubject.isEmpty()) {
            throw new AlreadyEnrolledInSubjectException(programEnrollmentId, subjectId);
        }

        validatePrerequisites(programEnrollment, section);

        Optional<SubjectSectionEnrollment> existing = subjectSectionEnrollmentRepository
                .findByStudentIdAndSubjectSectionId(student.getId(), section.getId());
        if (existing.isPresent()) {
            if (existing.get().getStatus() != EnrollmentStatus.WITHDRAWN) {
                throw new AlreadyEnrolledInSubjectException(programEnrollmentId, subjectId);
            }
            // Decisión 7: baja + reinscripción a la MISMA comisión reactiva la fila existente
            // (el unique constraint (student_id, program_section_id) no admite una fila nueva).
            SubjectSectionEnrollment reactivated = existing.get();
            reactivated.setStatus(calculateStatusByCapacity(section));
            reactivated.setDroppedAt(null);
            return subjectSectionEnrollmentRepository.save(reactivated);
        }

        SubjectSectionEnrollment newEnrollment = new SubjectSectionEnrollment();
        newEnrollment.setStudent(student);
        newEnrollment.setProgramEnrollment(programEnrollment);
        newEnrollment.setSubjectSection(section);
        newEnrollment.setStatus(calculateStatusByCapacity(section));
        newEnrollment.setEnrolledAt(LocalDateTime.now());
        newEnrollment.setAttemptNumber(calculateAttemptNumber(programEnrollmentId, subjectId, section));
        return subjectSectionEnrollmentRepository.save(newEnrollment);
    }

    /**
     * Decisión 8: un intento nuevo solo ocurre al anotarse a una comisión de un Term
     * DISTINTO para la misma materia, tras un intento anterior FAILED/WITHDRAWN. Si el
     * intento anterior más reciente terminó en la MISMA Term que la comisión nueva —algo
     * alcanzable dando de baja e inscribiendo manualmente sin pasar por cambiarComision—
     * no es un intento nuevo: se reusa su attemptNumber, mismo criterio que cambiarComision.
     */
    private int calculateAttemptNumber(Long programEnrollmentId, Long subjectId, SubjectSection newSection) {
        List<SubjectSectionEnrollment> previousAttempts = subjectSectionEnrollmentRepository
                .findByProgramEnrollmentIdAndSubjectSection_Subject_IdAndStatusIn(
                        programEnrollmentId, subjectId, List.of(EnrollmentStatus.FAILED, EnrollmentStatus.WITHDRAWN));
        if (previousAttempts.isEmpty()) {
            return 1;
        }
        SubjectSectionEnrollment lastAttempt = previousAttempts.stream()
                .max(Comparator.comparing(SubjectSectionEnrollment::getEnrolledAt))
                .orElseThrow();
        boolean sameTerm = lastAttempt.getSubjectSection().getTerm().getId().equals(newSection.getTerm().getId());
        return sameTerm ? lastAttempt.getAttemptNumber() : lastAttempt.getAttemptNumber() + 1;
    }

    @Transactional
    public void withdraw(Long id) {
        SubjectSectionEnrollment enrollment = findOrThrow(id);
        if (enrollment.getStatus() != EnrollmentStatus.ENROLLED && enrollment.getStatus() != EnrollmentStatus.IN_PROGRESS) {
            throw new CannotWithdrawException(
                    "La inscripción " + id + " no está ENROLLED/IN_PROGRESS, no se puede dar de baja");
        }
        enrollment.setStatus(EnrollmentStatus.WITHDRAWN);
        enrollment.setDroppedAt(LocalDateTime.now());
        subjectSectionEnrollmentRepository.save(enrollment);
        promoteNextInWaitlist(enrollment.getSubjectSection().getId());
    }

    @Transactional
    public SubjectSectionEnrollment changeSection(Long id, ChangeSectionRequest request) {
        SubjectSectionEnrollment currentEnrollment = findOrThrow(id);
        if (currentEnrollment.getStatus() != EnrollmentStatus.ENROLLED && currentEnrollment.getStatus() != EnrollmentStatus.IN_PROGRESS) {
            throw new CannotWithdrawException(
                    "La inscripción " + id + " no está ENROLLED/IN_PROGRESS, no se puede cambiar de comisión");
        }
        // A diferencia de withdraw (que tiene que poder cerrar una cursada aunque el alumno
        // ya no esté activo), cambiarComision mueve al alumno a una plaza nueva — no tiene
        // sentido dejar que alguien inactivo siga ocupando cupo de otras comisiones.
        validateStudentEligible(currentEnrollment.getStudent());

        SubjectSection targetSection = subjectSectionRepository.findById(request.newSectionId())
                .orElseThrow(() -> new SectionNotFoundException(request.newSectionId()));
        if (!targetSection.getSubject().getId().equals(currentEnrollment.getSubjectSection().getSubject().getId())) {
            throw new InvalidSectionChangeException(
                    "La comisión destino tiene que ser de la misma materia que la comisión origen");
        }
        // "Cambiar" a la comisión en la que ya está es un no-op: sin este corte, la baja+alta
        // de abajo dispara la promoción FIFO sobre la MISMA sección antes de reactivate la fila
        // origen, y el alumno pierde su propia plaza a manos de quien estaba en la lista de espera.
        if (targetSection.getId().equals(currentEnrollment.getSubjectSection().getId())) {
            return currentEnrollment;
        }
        // Decisión 3: cambiarComision es "misma materia, MISMO período" por definición — saltar
        // a otro Term es un intento nuevo (decisión 8), que corresponde a withdraw + enroll,
        // no a esta operación (que nunca recalcula attemptNumber).
        if (!targetSection.getTerm().getId().equals(currentEnrollment.getSubjectSection().getTerm().getId())) {
            throw new InvalidSectionChangeException(
                    "cambiarComision es solo para el mismo período (Term); para otro Term, dar de baja e enroll de nuevo");
        }
        validateEnrollmentWindow(targetSection.getTerm());

        Student student = currentEnrollment.getStudent();
        ProgramEnrollment programEnrollment = currentEnrollment.getProgramEnrollment();
        Integer attemptNumber = currentEnrollment.getAttemptNumber();
        LocalDateTime now = LocalDateTime.now();

        // Baja de la fila origen: dispara promoción de lista de espera igual que cualquier
        // baja, pero SIN pasar por withdraw() para no recalcular attemptNumber (decisión 3).
        currentEnrollment.setStatus(EnrollmentStatus.WITHDRAWN);
        currentEnrollment.setDroppedAt(now);
        subjectSectionEnrollmentRepository.save(currentEnrollment);
        promoteNextInWaitlist(currentEnrollment.getSubjectSection().getId());

        Optional<SubjectSectionEnrollment> existingTargetEnrollment = subjectSectionEnrollmentRepository
                .findByStudentIdAndSubjectSectionId(student.getId(), targetSection.getId());
        if (existingTargetEnrollment.isPresent() && existingTargetEnrollment.get().getStatus() == EnrollmentStatus.WITHDRAWN) {
            SubjectSectionEnrollment reactivated = existingTargetEnrollment.get();
            reactivated.setStatus(calculateStatusByCapacity(targetSection));
            reactivated.setDroppedAt(null);
            return subjectSectionEnrollmentRepository.save(reactivated);
        }

        SubjectSectionEnrollment newEnrollment = new SubjectSectionEnrollment();
        newEnrollment.setStudent(student);
        newEnrollment.setProgramEnrollment(programEnrollment);
        newEnrollment.setSubjectSection(targetSection);
        newEnrollment.setStatus(calculateStatusByCapacity(targetSection));
        newEnrollment.setEnrolledAt(now);
        newEnrollment.setAttemptNumber(attemptNumber);
        return subjectSectionEnrollmentRepository.save(newEnrollment);
    }

    public List<SubjectSectionEnrollment> listByEnrollment(Long programEnrollmentId) {
        return subjectSectionEnrollmentRepository.findByProgramEnrollmentId(programEnrollmentId);
    }

    // Autoservicio /me: abarca todas las carreras activas del alumno.
    public List<SubjectSectionEnrollment> listByStudent(Long studentId) {
        return subjectSectionEnrollmentRepository.findByStudentId(studentId);
    }

    // Épica 2 Fase 1 (Alumno · Mis carreras): el año curricular no vive en la inscripción
    // ni en la comisión — es propio del plan de LA carrera de esa inscripción puntual
    // (ProgramSubject.curricularYear, ver comentario de SubjectSection: una materia se
    // comparte entre carreras). Puede no existir (materia que ya no está en el plan
    // vigente, o vino de otra vía) — null en ese caso, el frontend lo trata como opcional.
    public Integer resolveCurricularYear(SubjectSectionEnrollment enrollment) {
        return resolveProgramSubject(enrollment).map(ProgramSubject::getCurricularYear).orElse(null);
    }

    // Cuatrimestre curricular de la materia dentro del plan de ESTA carrera puntual — mismo
    // criterio/lookup que resolverAnioCurricular (ProgramSubject depende del Program, una
    // materia compartida entre carreras puede tener año/cuatrimestre distinto en cada una).
    public CurricularTerm resolveCurricularTerm(SubjectSectionEnrollment enrollment) {
        return resolveProgramSubject(enrollment).map(ProgramSubject::getCurricularTerm).orElse(null);
    }

    private java.util.Optional<ProgramSubject> resolveProgramSubject(SubjectSectionEnrollment enrollment) {
        return programSubjectRepository.findByProgramIdAndSubjectId(
                enrollment.getProgramEnrollment().getProgram().getId(),
                enrollment.getSubjectSection().getSubject().getId());
    }

    public SubjectSectionEnrollment viewDetail(Long id) {
        return findOrThrow(id);
    }

    private void promoteNextInWaitlist(Long sectionId) {
        // Lock de fila: serializa esta promoción contra cualquier otra alta/baja/promoción
        // concurrente sobre la MISMA comisión (ver findByIdForUpdate) — sin esto, dos bajas
        // simultáneas pueden leer el mismo "primero en la lista de espera" y promoverlo dos
        // veces, o perder una promoción por un OptimisticLockException no controlado.
        subjectSectionRepository.findByIdForUpdate(sectionId)
                .orElseThrow(() -> new SectionNotFoundException(sectionId));
        subjectSectionEnrollmentRepository
                .findFirstBySubjectSectionIdAndStatusOrderByEnrolledAtAsc(sectionId, EnrollmentStatus.WAITLISTED)
                .ifPresent(next -> {
                    next.setStatus(EnrollmentStatus.ENROLLED);
                    subjectSectionEnrollmentRepository.save(next);
                });
    }

    private EnrollmentStatus calculateStatusByCapacity(SubjectSection section) {
        // Mismo lock que promoteNextInWaitlist: sin serializar acá, dos altas
        // concurrentes a una comisión con capacity=1 pueden leer "ocupadas=0" ambas antes de
        // que cualquiera haga commit, y las dos terminar ENROLLED por encima del cupo real.
        subjectSectionRepository.findByIdForUpdate(section.getId())
                .orElseThrow(() -> new SectionNotFoundException(section.getId()));
        int occupied = subjectSectionEnrollmentRepository
                .findBySubjectSectionIdAndStatusIn(section.getId(),
                        List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS, EnrollmentStatus.REGULAR))
                .size();
        return section.getCapacity() == null || occupied < section.getCapacity()
                ? EnrollmentStatus.ENROLLED : EnrollmentStatus.WAITLISTED;
    }

    /**
     * Scopeada a la misma ProgramEnrollment (misma carrera): si el Subject se comparte
     * entre carreras, aprobarlo bajo otra carrera no cuenta acá (Spec Inscripciones,
     * sección 6 — coherente con la decisión 16 de Estructura académica).
     */
    private void validatePrerequisites(ProgramEnrollment programEnrollment, SubjectSection section) {
        ProgramSubject programSubject = programSubjectRepository
                .findByProgramIdAndSubjectId(programEnrollment.getProgram().getId(), section.getSubject().getId())
                .orElseThrow(() -> new SubjectNotInProgramPlanException(
                        section.getSubject().getId(), programEnrollment.getProgram().getId()));

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

    private void validateActiveProgramEnrollment(ProgramEnrollment programEnrollment) {
        if (programEnrollment.getStatus() != ProgramEnrollmentStatus.ENROLLED
                && programEnrollment.getStatus() != ProgramEnrollmentStatus.PAUSED) {
            throw new ProgramEnrollmentRequiredException(programEnrollment.getId());
        }
    }

    private void validateEnrollmentWindow(Term term) {
        // Decisión 5 (Spec Procesos administrativos): el override manual tiene prioridad sobre
        // la validación por fecha de acá abajo, sin tocarla ni duplicarla. null = sin cambios.
        if (term.getEnrollmentOverride() == TermEnrollmentOverride.CLOSED) {
            throw new TermEnrollmentOverrideClosedException(term.getId());
        }
        if (term.getEnrollmentOverride() == TermEnrollmentOverride.OPEN) {
            return;
        }
        if (term.getEnrollmentStart() != null && term.getEnrollmentEnd() != null
                && term.getEnrollmentStart().isAfter(term.getEnrollmentEnd())) {
            throw new InvalidWindowConfigurationException(
                    "El Term " + term.getId() + " tiene la ventana de inscripción mal configurada "
                            + "(enrollmentStart posterior a enrollmentEnd)");
        }
        LocalDateTime now = LocalDateTime.now();
        if (term.getEnrollmentStart() != null && now.isBefore(term.getEnrollmentStart())
                || term.getEnrollmentEnd() != null && now.isAfter(term.getEnrollmentEnd())) {
            throw new EnrollmentWindowClosedException("Fuera de la ventana de inscripción del Term " + term.getId());
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

    private ProgramEnrollment findProgramEnrollmentOrThrow(Long id) {
        return programEnrollmentRepository.findById(id)
                .orElseThrow(() -> new ProgramEnrollmentNotFoundException(id));
    }

    private SubjectSectionEnrollment findOrThrow(Long id) {
        return subjectSectionEnrollmentRepository.findById(id)
                .orElseThrow(() -> new SectionEnrollmentNotFoundException(id));
    }
}
