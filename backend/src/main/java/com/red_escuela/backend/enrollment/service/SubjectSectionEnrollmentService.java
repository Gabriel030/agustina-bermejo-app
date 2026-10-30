package com.red_escuela.backend.enrollment.service;

import java.time.Clock;
import com.red_escuela.backend.enrollment.dto.ChangeSectionRequest;
import com.red_escuela.backend.enrollment.dto.CreateSectionEnrollmentRequest;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.billing.service.EnrollmentFeeRequirementService;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.academicstructure.domain.CurricularTerm;
import com.red_escuela.backend.academicstructure.domain.RequirementType;
import com.red_escuela.backend.enrollment.entity.ExceptionType;
import com.red_escuela.backend.enrollment.repository.DirectorApprovalRequestRepository;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.api.StudentEnrollmentEligibility;
import com.red_escuela.backend.academicstructure.api.AcademicReferenceFinder;
import com.red_escuela.backend.academicstructure.api.PrerequisiteView;
import com.red_escuela.backend.academicstructure.api.ProgramCurriculumFinder;
import com.red_escuela.backend.academicstructure.api.ProgramSectionView;
import com.red_escuela.backend.academicstructure.api.ProgramSubjectView;
import com.red_escuela.backend.academicstructure.api.SectionFinder;
import com.red_escuela.backend.academicstructure.api.TermEnrollmentWindowFinder;
import com.red_escuela.backend.academicstructure.api.TermEnrollmentWindowView;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.exception.AlreadyEnrolledInSubjectException;
import com.red_escuela.backend.enrollment.exception.CannotWithdrawException;
import com.red_escuela.backend.enrollment.exception.EnrollmentWindowClosedException;
import com.red_escuela.backend.enrollment.exception.InvalidSectionChangeException;
import com.red_escuela.backend.enrollment.exception.InvalidWindowConfigurationException;
import com.red_escuela.backend.enrollment.exception.PrerequisiteNotMetException;
import com.red_escuela.backend.enrollment.exception.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.exception.ProgramEnrollmentRequiredException;
import com.red_escuela.backend.enrollment.exception.SectionEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.exception.SubjectNotInProgramPlanException;
import com.red_escuela.backend.enrollment.exception.TermEnrollmentOverrideClosedException;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository;
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
    private final SectionFinder sectionFinder;
    private final TermEnrollmentWindowFinder termEnrollmentWindowFinder;
    private final AcademicReferenceFinder academicReferenceFinder;
    private final ProgramCurriculumFinder programCurriculumFinder;
    private final InvoiceRepository invoiceRepository;
    private final DirectorApprovalRequestRepository directorApprovalRequestRepository;
    private final EnrollmentFeeRequirementService enrollmentFeeRequirementService;
    private final StudentEnrollmentEligibility studentEnrollmentEligibility;
    private final Clock clock;

    public SubjectSectionEnrollmentService(SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository,
            ProgramEnrollmentRepository programEnrollmentRepository,
            SectionFinder sectionFinder,
            TermEnrollmentWindowFinder termEnrollmentWindowFinder,
            AcademicReferenceFinder academicReferenceFinder,
            ProgramCurriculumFinder programCurriculumFinder,
            InvoiceRepository invoiceRepository,
            DirectorApprovalRequestRepository directorApprovalRequestRepository,
            EnrollmentFeeRequirementService enrollmentFeeRequirementService,
            StudentEnrollmentEligibility studentEnrollmentEligibility,
            Clock clock) {
        this.enrollmentFeeRequirementService = enrollmentFeeRequirementService;
        this.studentEnrollmentEligibility = studentEnrollmentEligibility;
        this.subjectSectionEnrollmentRepository = subjectSectionEnrollmentRepository;
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.sectionFinder = sectionFinder;
        this.termEnrollmentWindowFinder = termEnrollmentWindowFinder;
        this.academicReferenceFinder = academicReferenceFinder;
        this.programCurriculumFinder = programCurriculumFinder;
        this.invoiceRepository = invoiceRepository;
        this.directorApprovalRequestRepository = directorApprovalRequestRepository;
        this.clock = clock;
    }

    @Transactional
    public SubjectSectionEnrollment enroll(Long programEnrollmentId, CreateSectionEnrollmentRequest request) {
        ProgramEnrollment programEnrollment = findProgramEnrollmentOrThrow(programEnrollmentId);
        validateActiveProgramEnrollment(programEnrollment);
        Student student = programEnrollment.getStudent();
        studentEnrollmentEligibility.requireConfirmedAndActive(student);

        ProgramSectionView section = sectionFinder.findProgramSection(request.sectionId());
        validateEnrollmentWindow(section.termId());
        enrollmentFeeRequirementService.requireForTerm(programEnrollment, academicReferenceFinder.findTerm(section.termId()));

        Long subjectId = section.subjectId();
        List<SubjectSectionEnrollment> activasMismaMateria = subjectSectionEnrollmentRepository
                .findByProgramEnrollmentIdAndSubjectSection_Subject_IdAndStatusIn(
                        programEnrollmentId, subjectId,
                        List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS, EnrollmentStatus.WAITLISTED));
        if (!activasMismaMateria.isEmpty()) {
            throw new AlreadyEnrolledInSubjectException(programEnrollmentId, subjectId);
        }

        validatePrerequisites(programEnrollment, subjectId);

        Optional<SubjectSectionEnrollment> existente = subjectSectionEnrollmentRepository
                .findByStudentIdAndSubjectSectionId(student.getId(), section.id());
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
        nueva.setSubjectSection(sectionFinder.findProgramSectionEntity(section.id()));
        nueva.setStatus(calculateStatusByCapacity(section));
        nueva.setEnrolledAt(LocalDateTime.now(clock));
        nueva.setAttemptNumber(calculateAttemptNumber(programEnrollmentId, subjectId, section.termId()));
        return subjectSectionEnrollmentRepository.save(nueva);
    }

    private int calculateAttemptNumber(Long programEnrollmentId, Long subjectId, Long newTermId) {
        List<SubjectSectionEnrollment> previas = subjectSectionEnrollmentRepository
                .findByProgramEnrollmentIdAndSubjectSection_Subject_IdAndStatusIn(
                        programEnrollmentId, subjectId, List.of(EnrollmentStatus.FAILED, EnrollmentStatus.WITHDRAWN));
        if (previas.isEmpty()) {
            return 1;
        }
        SubjectSectionEnrollment ultima = previas.stream()
                .max(Comparator.comparing(SubjectSectionEnrollment::getEnrolledAt))
                .orElseThrow();
        boolean mismoTerm = ultima.termId().equals(newTermId);
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
        enrollment.setDroppedAt(LocalDateTime.now(clock));
        subjectSectionEnrollmentRepository.save(enrollment);
        promoteNextFromWaitlist(enrollment.subjectSectionId());
    }

    @Transactional
    public SubjectSectionEnrollment changeSection(Long id, ChangeSectionRequest request) {
        SubjectSectionEnrollment origen = findOrThrow(id);
        if (origen.getStatus() != EnrollmentStatus.ENROLLED && origen.getStatus() != EnrollmentStatus.IN_PROGRESS) {
            throw new CannotWithdrawException(
                    "La inscripción " + id + " no está ENROLLED/IN_PROGRESS, no se puede cambiar de comisión");
        }
        studentEnrollmentEligibility.requireConfirmedAndActive(origen.getStudent());

        ProgramSectionView destino = sectionFinder.findProgramSection(request.newSectionId());
        if (!destino.subjectId().equals(origen.subjectId())) {
            throw new InvalidSectionChangeException(
                    "La comisión destino tiene que ser de la misma materia que la comisión origen");
        }
        if (destino.id().equals(origen.subjectSectionId())) {
            return origen;
        }
        if (!destino.termId().equals(origen.termId())) {
            throw new InvalidSectionChangeException(
                    "changeSection es solo para el mismo período (Term); para otro Term, dar de baja e enroll de nuevo");
        }
        validateEnrollmentWindow(destino.termId());

        Student student = origen.getStudent();
        ProgramEnrollment programEnrollment = origen.getProgramEnrollment();
        Integer attemptNumber = origen.getAttemptNumber();
        LocalDateTime now = LocalDateTime.now(clock);

        origen.setStatus(EnrollmentStatus.WITHDRAWN);
        origen.setDroppedAt(now);
        subjectSectionEnrollmentRepository.save(origen);
        promoteNextFromWaitlist(origen.subjectSectionId());

        Optional<SubjectSectionEnrollment> existenteDestino = subjectSectionEnrollmentRepository
                .findByStudentIdAndSubjectSectionId(student.getId(), destino.id());
        if (existenteDestino.isPresent() && existenteDestino.get().getStatus() == EnrollmentStatus.WITHDRAWN) {
            SubjectSectionEnrollment reactivada = existenteDestino.get();
            reactivada.setStatus(calculateStatusByCapacity(destino));
            reactivada.setDroppedAt(null);
            return subjectSectionEnrollmentRepository.save(reactivada);
        }

        SubjectSectionEnrollment nueva = new SubjectSectionEnrollment();
        nueva.setStudent(student);
        nueva.setProgramEnrollment(programEnrollment);
        nueva.setSubjectSection(sectionFinder.findProgramSectionEntity(destino.id()));
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
        return resolveProgramSubject(enrollment).map(ProgramSubjectView::curricularYear).orElse(null);
    }

    public CurricularTerm resolveCurricularTerm(SubjectSectionEnrollment enrollment) {
        return resolveProgramSubject(enrollment).map(ProgramSubjectView::curricularTerm).orElse(null);
    }

    private Optional<ProgramSubjectView> resolveProgramSubject(SubjectSectionEnrollment enrollment) {
        return programCurriculumFinder.findProgramSubject(
                enrollment.getProgramEnrollment().getProgram().getId(),
                enrollment.subjectId());
    }

    public SubjectSectionEnrollment get(Long id) {
        return findOrThrow(id);
    }

    private void promoteNextFromWaitlist(Long sectionId) {
        sectionFinder.lockProgramSection(sectionId);
        subjectSectionEnrollmentRepository
                .findFirstBySubjectSectionIdAndStatusOrderByEnrolledAtAsc(sectionId, EnrollmentStatus.WAITLISTED)
                .ifPresent(siguiente -> {
                    siguiente.setStatus(EnrollmentStatus.ENROLLED);
                    subjectSectionEnrollmentRepository.save(siguiente);
                });
    }

    private EnrollmentStatus calculateStatusByCapacity(ProgramSectionView section) {
        sectionFinder.lockProgramSection(section.id());
        int ocupadas = subjectSectionEnrollmentRepository
                .findBySubjectSectionIdAndStatusIn(section.id(),
                        List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS, EnrollmentStatus.REGULAR))
                .size();
        return section.capacity() == null || ocupadas < section.capacity()
                ? EnrollmentStatus.ENROLLED : EnrollmentStatus.WAITLISTED;
    }

    private void validatePrerequisites(ProgramEnrollment programEnrollment, Long subjectId) {
        ProgramSubjectView programSubject = programCurriculumFinder
                .findProgramSubject(programEnrollment.getProgram().getId(), subjectId)
                .orElseThrow(() -> new SubjectNotInProgramPlanException(
                        subjectId, programEnrollment.getProgram().getId()));
        if (directorApprovalRequestRepository.hasApproved(
                programEnrollment.getId(), subjectId, ExceptionType.PREREQUISITE_WAIVER)) {
            return;
        }

        for (PrerequisiteView prereq : programCurriculumFinder.findPrerequisites(programSubject.id())) {
            List<EnrollmentStatus> estadosQueSirven = prereq.requirementType() == RequirementType.APPROVED
                    ? List.of(EnrollmentStatus.APPROVED)
                    : List.of(EnrollmentStatus.REGULAR, EnrollmentStatus.APPROVED);

            boolean cumplida = !subjectSectionEnrollmentRepository
                    .findByProgramEnrollmentIdAndSubjectSection_Subject_IdAndStatusIn(
                            programEnrollment.getId(), prereq.prerequisiteSubjectId(),
                            estadosQueSirven)
                    .isEmpty();
            if (!cumplida) {
                throw new PrerequisiteNotMetException(
                        "No se cumple la correlatividad: " + prereq.prerequisiteSubjectName());
            }
        }
    }

    private void validateActiveProgramEnrollment(ProgramEnrollment programEnrollment) {
        if (programEnrollment.getStatus() != ProgramEnrollmentStatus.ENROLLED
                && programEnrollment.getStatus() != ProgramEnrollmentStatus.PAUSED) {
            throw new ProgramEnrollmentRequiredException(programEnrollment.getId());
        }
    }

    private void validateEnrollmentWindow(Long termId) {
        TermEnrollmentWindowView window = termEnrollmentWindowFinder.findEnrollmentWindow(termId, LocalDateTime.now(clock));
        if (window.closedByOverride()) {
            throw new TermEnrollmentOverrideClosedException(termId);
        }
        if (window.windowInverted()) {
            throw new InvalidWindowConfigurationException(
                    "El Term " + termId + " tiene la ventana de inscripción mal configurada "
                            + "(enrollmentStart posterior a enrollmentEnd)");
        }
        if (!window.open()) {
            throw new EnrollmentWindowClosedException("Fuera de la ventana de inscripción del Term " + termId);
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
