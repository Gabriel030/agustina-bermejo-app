package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.dto.ChangeSectionRequest;
import com.red_escuela.backend.enrollment.dto.CreateSectionEnrollmentRequest;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.academicstructure.entity.CurricularTerm;
import com.red_escuela.backend.academicstructure.entity.ProgramSubject;
import com.red_escuela.backend.academicstructure.entity.ProgramSubjectPrerequisite;
import com.red_escuela.backend.academicstructure.entity.RequirementType;
import com.red_escuela.backend.entity.ExceptionType;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.enrollment.repository.DirectorApprovalRequestRepository;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.entity.TermEnrollmentOverride;
import com.red_escuela.backend.enrollment.exception.AlreadyEnrolledInSubjectException;
import com.red_escuela.backend.enrollment.exception.CannotWithdrawException;
import com.red_escuela.backend.enrollment.exception.EnrollmentFeeNotPaidException;
import com.red_escuela.backend.enrollment.exception.EnrollmentWindowClosedException;
import com.red_escuela.backend.enrollment.exception.InvalidSectionChangeException;
import com.red_escuela.backend.enrollment.exception.InvalidWindowConfigurationException;
import com.red_escuela.backend.enrollment.exception.PrerequisiteNotMetException;
import com.red_escuela.backend.enrollment.exception.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.exception.ProgramEnrollmentRequiredException;
import com.red_escuela.backend.enrollment.exception.SectionEnrollmentNotFoundException;
import com.red_escuela.backend.academicstructure.exception.SectionNotFoundException;
import com.red_escuela.backend.identity.exception.StudentNotActiveException;
import com.red_escuela.backend.identity.exception.StudentNotConfirmedException;
import com.red_escuela.backend.enrollment.exception.SubjectNotInProgramPlanException;
import com.red_escuela.backend.enrollment.exception.TermEnrollmentOverrideClosedException;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramSubjectPrerequisiteRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramSubjectRepository;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class SubjectSectionEnrollmentService {
    private final SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository;
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final SubjectSectionRepository subjectSectionRepository;
    private final ProgramSubjectRepository programSubjectRepository;
    private final ProgramSubjectPrerequisiteRepository prerequisiteRepository;
    private final InvoiceRepository invoiceRepository;
    private final DirectorApprovalRequestRepository directorApprovalRequestRepository;

    public SubjectSectionEnrollmentService(
            SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository,
            ProgramEnrollmentRepository programEnrollmentRepository,
            SubjectSectionRepository subjectSectionRepository,
            ProgramSubjectRepository programSubjectRepository,
            ProgramSubjectPrerequisiteRepository prerequisiteRepository,
            InvoiceRepository invoiceRepository,
            DirectorApprovalRequestRepository directorApprovalRequestRepository) {
        this.subjectSectionEnrollmentRepository = subjectSectionEnrollmentRepository;
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.subjectSectionRepository = subjectSectionRepository;
        this.programSubjectRepository = programSubjectRepository;
        this.prerequisiteRepository = prerequisiteRepository;
        this.invoiceRepository = invoiceRepository;
        this.directorApprovalRequestRepository = directorApprovalRequestRepository;
    }

    @Transactional
    public SubjectSectionEnrollment enroll(Long programEnrollmentId, CreateSectionEnrollmentRequest request) {
        ProgramEnrollment programEnrollment = findProgramEnrollmentOrThrow(programEnrollmentId);
        validateActiveProgramEnrollment(programEnrollment);
        Student student = programEnrollment.getStudent();
        validateStudentEligible(student);
        validateEnrollmentFeePaid(programEnrollment);

        SubjectSection section = subjectSectionRepository.findById(request.sectionId())
                .orElseThrow(() -> new SectionNotFoundException(request.sectionId()));
        validateEnrollmentWindow(section.getTerm());

        Long subjectId = section.getSubject().getId();
        List<SubjectSectionEnrollment> activasMismaMateria = subjectSectionEnrollmentRepository
                .findByProgramEnrollmentIdAndSubjectSection_Subject_IdAndStatusIn(
                        programEnrollmentId, subjectId,
                        List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS, EnrollmentStatus.WAITLISTED));
        if (!activasMismaMateria.isEmpty()) {
            throw new AlreadyEnrolledInSubjectException(programEnrollmentId, subjectId);
        }

        validatePrerequisites(programEnrollment, section);

        Optional<SubjectSectionEnrollment> existente = subjectSectionEnrollmentRepository
                .findByStudentIdAndSubjectSectionId(student.getId(), section.getId());
        if (existente.isPresent()) {
            if (existente.get().getStatus() != EnrollmentStatus.WITHDRAWN) {
                throw new AlreadyEnrolledInSubjectException(programEnrollmentId, subjectId);
            }
            SubjectSectionEnrollment reactivada = existente.get();
            reactivada.setStatus(calculateStatusByCapacity(section));
            reactivada.setDroppedAt(null);
            return subjectSectionEnrollmentRepository.save(reactivada);
        }

        SubjectSectionEnrollment nueva = new SubjectSectionEnrollment();
        nueva.setStudent(student);
        nueva.setProgramEnrollment(programEnrollment);
        nueva.setSubjectSection(section);
        nueva.setStatus(calculateStatusByCapacity(section));
        nueva.setEnrolledAt(LocalDateTime.now());
        nueva.setAttemptNumber(calculateAttemptNumber(programEnrollmentId, subjectId, section));
        return subjectSectionEnrollmentRepository.save(nueva);
    }

    private int calculateAttemptNumber(Long programEnrollmentId, Long subjectId, SubjectSection nuevaSeccion) {
        List<SubjectSectionEnrollment> previas = subjectSectionEnrollmentRepository
                .findByProgramEnrollmentIdAndSubjectSection_Subject_IdAndStatusIn(
                        programEnrollmentId, subjectId, List.of(EnrollmentStatus.FAILED, EnrollmentStatus.WITHDRAWN));
        if (previas.isEmpty()) {
            return 1;
        }
        SubjectSectionEnrollment ultima = previas.stream()
                .max(Comparator.comparing(SubjectSectionEnrollment::getEnrolledAt))
                .orElseThrow();
        boolean mismoTerm = ultima.getSubjectSection().getTerm().getId().equals(nuevaSeccion.getTerm().getId());
        return mismoTerm ? ultima.getAttemptNumber() : ultima.getAttemptNumber() + 1;
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
        promoteNextFromWaitlist(enrollment.getSubjectSection().getId());
    }

    @Transactional
    public SubjectSectionEnrollment changeSection(Long id, ChangeSectionRequest request) {
        SubjectSectionEnrollment origen = findOrThrow(id);
        if (origen.getStatus() != EnrollmentStatus.ENROLLED && origen.getStatus() != EnrollmentStatus.IN_PROGRESS) {
            throw new CannotWithdrawException(
                    "La inscripción " + id + " no está ENROLLED/IN_PROGRESS, no se puede cambiar de comisión");
        }
        validateStudentEligible(origen.getStudent());

        SubjectSection destino = subjectSectionRepository.findById(request.newSectionId())
                .orElseThrow(() -> new SectionNotFoundException(request.newSectionId()));
        if (!destino.getSubject().getId().equals(origen.getSubjectSection().getSubject().getId())) {
            throw new InvalidSectionChangeException(
                    "La comisión destino tiene que ser de la misma materia que la comisión origen");
        }
        if (destino.getId().equals(origen.getSubjectSection().getId())) {
            return origen;
        }
        if (!destino.getTerm().getId().equals(origen.getSubjectSection().getTerm().getId())) {
            throw new InvalidSectionChangeException(
                    "changeSection es solo para el mismo período (Term); para otro Term, dar de baja e enroll de nuevo");
        }
        validateEnrollmentWindow(destino.getTerm());

        Student student = origen.getStudent();
        ProgramEnrollment programEnrollment = origen.getProgramEnrollment();
        Integer attemptNumber = origen.getAttemptNumber();
        LocalDateTime now = LocalDateTime.now();

        origen.setStatus(EnrollmentStatus.WITHDRAWN);
        origen.setDroppedAt(now);
        subjectSectionEnrollmentRepository.save(origen);
        promoteNextFromWaitlist(origen.getSubjectSection().getId());

        Optional<SubjectSectionEnrollment> existenteDestino = subjectSectionEnrollmentRepository
                .findByStudentIdAndSubjectSectionId(student.getId(), destino.getId());
        if (existenteDestino.isPresent() && existenteDestino.get().getStatus() == EnrollmentStatus.WITHDRAWN) {
            SubjectSectionEnrollment reactivada = existenteDestino.get();
            reactivada.setStatus(calculateStatusByCapacity(destino));
            reactivada.setDroppedAt(null);
            return subjectSectionEnrollmentRepository.save(reactivada);
        }

        SubjectSectionEnrollment nueva = new SubjectSectionEnrollment();
        nueva.setStudent(student);
        nueva.setProgramEnrollment(programEnrollment);
        nueva.setSubjectSection(destino);
        nueva.setStatus(calculateStatusByCapacity(destino));
        nueva.setEnrolledAt(now);
        nueva.setAttemptNumber(attemptNumber);
        return subjectSectionEnrollmentRepository.save(nueva);
    }

    public List<SubjectSectionEnrollment> listByEnrollment(Long programEnrollmentId) {
        return subjectSectionEnrollmentRepository.findByProgramEnrollmentId(programEnrollmentId);
    }

    public List<SubjectSectionEnrollment> listByStudent(Long studentId) {
        return subjectSectionEnrollmentRepository.findByStudentId(studentId);
    }

    public Integer resolveCurricularYear(SubjectSectionEnrollment enrollment) {
        return resolveProgramSubject(enrollment).map(ProgramSubject::getCurricularYear).orElse(null);
    }

    public CurricularTerm resolveCurricularTerm(SubjectSectionEnrollment enrollment) {
        return resolveProgramSubject(enrollment).map(ProgramSubject::getCurricularTerm).orElse(null);
    }

    private java.util.Optional<ProgramSubject> resolveProgramSubject(SubjectSectionEnrollment enrollment) {
        return programSubjectRepository.findByProgramIdAndSubjectId(
                enrollment.getProgramEnrollment().getProgram().getId(),
                enrollment.getSubjectSection().getSubject().getId());
    }

    public SubjectSectionEnrollment get(Long id) {
        return findOrThrow(id);
    }

    private void promoteNextFromWaitlist(Long sectionId) {
        subjectSectionRepository.findByIdForUpdate(sectionId)
                .orElseThrow(() -> new SectionNotFoundException(sectionId));
        subjectSectionEnrollmentRepository
                .findFirstBySubjectSectionIdAndStatusOrderByEnrolledAtAsc(sectionId, EnrollmentStatus.WAITLISTED)
                .ifPresent(siguiente -> {
                    siguiente.setStatus(EnrollmentStatus.ENROLLED);
                    subjectSectionEnrollmentRepository.save(siguiente);
                });
    }

    private EnrollmentStatus calculateStatusByCapacity(SubjectSection section) {
        subjectSectionRepository.findByIdForUpdate(section.getId())
                .orElseThrow(() -> new SectionNotFoundException(section.getId()));
        int ocupadas = subjectSectionEnrollmentRepository
                .findBySubjectSectionIdAndStatusIn(section.getId(),
                        List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS, EnrollmentStatus.REGULAR))
                .size();
        return section.getCapacity() == null || ocupadas < section.getCapacity()
                ? EnrollmentStatus.ENROLLED : EnrollmentStatus.WAITLISTED;
    }

    private void validatePrerequisites(ProgramEnrollment programEnrollment, SubjectSection section) {
        ProgramSubject programSubject = programSubjectRepository
                .findByProgramIdAndSubjectId(programEnrollment.getProgram().getId(), section.getSubject().getId())
                .orElseThrow(() -> new SubjectNotInProgramPlanException(
                        section.getSubject().getId(), programEnrollment.getProgram().getId()));
        if (directorApprovalRequestRepository.hasApproved(
                programEnrollment.getId(), section.getSubject().getId(), ExceptionType.PREREQUISITE_WAIVER)) {
            return;
        }

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

    private void validateEnrollmentFeePaid(ProgramEnrollment programEnrollment) {
        Invoice enrollmentFeeInvoice = invoiceRepository
                .findFirstByProgramEnrollmentIdAndFeeTypeOrderByPeriodDesc(programEnrollment.getId(), FeeType.ENROLLMENT_FEE)
                .orElse(null);
        if (!programEnrollment.canEnrollInSubjects(enrollmentFeeInvoice)) {
            throw new EnrollmentFeeNotPaidException(programEnrollment.getId());
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
