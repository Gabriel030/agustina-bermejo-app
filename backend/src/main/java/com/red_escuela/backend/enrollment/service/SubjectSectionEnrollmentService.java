package com.red_escuela.backend.enrollment.service;

import java.time.Clock;
import com.red_escuela.backend.enrollment.api.FinalExamEligibilityResolver;
import com.red_escuela.backend.enrollment.dto.ChangeSectionRequest;
import com.red_escuela.backend.enrollment.dto.CreateSectionEnrollmentRequest;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.billing.service.EnrollmentFeeRequirementService;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.academicstructure.domain.CurricularTerm;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.api.StudentEnrollmentEligibility;
import com.red_escuela.backend.academicstructure.api.AcademicReferenceFinder;
import com.red_escuela.backend.academicstructure.api.ProgramCurriculumFinder;
import com.red_escuela.backend.academicstructure.api.ProgramSectionView;
import com.red_escuela.backend.academicstructure.api.ProgramSubjectView;
import com.red_escuela.backend.academicstructure.api.SectionFinder;
import com.red_escuela.backend.academicstructure.api.TermEnrollmentWindowFinder;
import com.red_escuela.backend.academicstructure.api.TermEnrollmentWindowView;
import com.red_escuela.backend.enrollment.api.ProgramPrerequisiteEvaluator;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.exception.AlreadyEnrolledInSubjectException;
import com.red_escuela.backend.enrollment.exception.CannotWithdrawException;
import com.red_escuela.backend.enrollment.exception.EnrollmentWindowClosedException;
import com.red_escuela.backend.enrollment.exception.InvalidSectionChangeException;
import com.red_escuela.backend.enrollment.exception.SectionFullException;
import com.red_escuela.backend.enrollment.exception.InvalidWindowConfigurationException;
import com.red_escuela.backend.enrollment.exception.PrerequisiteNotMetException;
import com.red_escuela.backend.enrollment.exception.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.exception.ProgramEnrollmentRequiredException;
import com.red_escuela.backend.enrollment.exception.SectionEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.exception.TermEnrollmentOverrideClosedException;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import com.red_escuela.backend.enrollment.dto.SectionEnrollmentResponse;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SubjectSectionEnrollmentService {
    private final SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository;
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final SectionFinder sectionFinder;
    private final TermEnrollmentWindowFinder termEnrollmentWindowFinder;
    private final AcademicReferenceFinder academicReferenceFinder;
    private final ProgramCurriculumFinder programCurriculumFinder;
    private final InvoiceRepository invoiceRepository;
    private final ProgramPrerequisiteEvaluator prerequisiteEvaluator;
    private final EnrollmentFeeRequirementService enrollmentFeeRequirementService;
    private final StudentEnrollmentEligibility studentEnrollmentEligibility;
    private final SectionWaitlistPromoter waitlistPromoter;
    private final SubjectRetakeGuard subjectRetakeGuard;
    private final FinalExamEligibilityResolver finalExamEligibilityResolver;
    private final Clock clock;

    public SubjectSectionEnrollmentService(SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository,
            ProgramEnrollmentRepository programEnrollmentRepository,
            SectionFinder sectionFinder,
            TermEnrollmentWindowFinder termEnrollmentWindowFinder,
            AcademicReferenceFinder academicReferenceFinder,
            ProgramCurriculumFinder programCurriculumFinder,
            InvoiceRepository invoiceRepository,
            ProgramPrerequisiteEvaluator prerequisiteEvaluator,
            EnrollmentFeeRequirementService enrollmentFeeRequirementService,
            StudentEnrollmentEligibility studentEnrollmentEligibility,
            SectionWaitlistPromoter waitlistPromoter,
            SubjectRetakeGuard subjectRetakeGuard,
            FinalExamEligibilityResolver finalExamEligibilityResolver,
            Clock clock) {
        this.subjectRetakeGuard = subjectRetakeGuard;
        this.finalExamEligibilityResolver = finalExamEligibilityResolver;
        this.enrollmentFeeRequirementService = enrollmentFeeRequirementService;
        this.studentEnrollmentEligibility = studentEnrollmentEligibility;
        this.waitlistPromoter = waitlistPromoter;
        this.subjectSectionEnrollmentRepository = subjectSectionEnrollmentRepository;
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.sectionFinder = sectionFinder;
        this.termEnrollmentWindowFinder = termEnrollmentWindowFinder;
        this.academicReferenceFinder = academicReferenceFinder;
        this.programCurriculumFinder = programCurriculumFinder;
        this.invoiceRepository = invoiceRepository;
        this.prerequisiteEvaluator = prerequisiteEvaluator;
        this.clock = clock;
    }

    @Transactional
    public SubjectSectionEnrollment enroll(Long programEnrollmentId, CreateSectionEnrollmentRequest request) {
        ProgramEnrollment programEnrollment = findProgramEnrollmentOrThrow(programEnrollmentId);
        validateActiveProgramEnrollment(programEnrollment);
        Student student = programEnrollment.getStudent();
        studentEnrollmentEligibility.requireConfirmedAndActive(student);

        ProgramSectionView section = sectionFinder.findOpenProgramSection(request.sectionId());
        validateEnrollmentWindow(section.termId());
        enrollmentFeeRequirementService.requireForTerm(programEnrollment, academicReferenceFinder.findTerm(section.termId()));

        programEnrollment = lockProgramEnrollmentOrThrow(programEnrollmentId);
        validateActiveProgramEnrollment(programEnrollment);

        Long subjectId = section.subjectId();
        List<SubjectSectionEnrollment> activasMismaMateria = subjectSectionEnrollmentRepository
                .findByProgramEnrollmentIdAndSubjectSection_Subject_IdAndStatusIn(
                        programEnrollmentId, subjectId,
                        List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS, EnrollmentStatus.WAITLISTED));
        if (!activasMismaMateria.isEmpty()) {
            throw new AlreadyEnrolledInSubjectException(programEnrollmentId, subjectId);
        }

        subjectRetakeGuard.requireNotAlreadyPassed(programEnrollmentId, subjectId, null);
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
            reactivada.setEnrolledAt(LocalDateTime.now(clock));
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
        sectionFinder.lockProgramSection(findSubjectSectionIdOrThrow(id));
        SubjectSectionEnrollment enrollment = findOrThrow(id);
        EnrollmentStatus previousStatus = enrollment.getStatus();
        if (previousStatus != EnrollmentStatus.ENROLLED && previousStatus != EnrollmentStatus.IN_PROGRESS
                && previousStatus != EnrollmentStatus.WAITLISTED) {
            throw new CannotWithdrawException(
                    "La inscripción " + id + " no está ENROLLED/IN_PROGRESS/WAITLISTED, no se puede dar de baja");
        }
        enrollment.setStatus(EnrollmentStatus.WITHDRAWN);
        enrollment.setDroppedAt(LocalDateTime.now(clock));
        subjectSectionEnrollmentRepository.save(enrollment);
        if (previousStatus != EnrollmentStatus.WAITLISTED) {
            promoteNextFromWaitlist(enrollment.subjectSectionId());
        }
    }

    @Transactional
    public SubjectSectionEnrollment changeSection(Long id, ChangeSectionRequest request) {
        lockProgramEnrollmentOrThrow(findProgramEnrollmentIdOrThrow(id));
        lockSectionsInAscendingOrder(findSubjectSectionIdOrThrow(id), request.newSectionId());
        SubjectSectionEnrollment origen = findOrThrow(id);
        if (origen.getStatus() != EnrollmentStatus.ENROLLED && origen.getStatus() != EnrollmentStatus.IN_PROGRESS) {
            throw new CannotWithdrawException(
                    "La inscripción " + id + " no está ENROLLED/IN_PROGRESS, no se puede cambiar de comisión");
        }
        studentEnrollmentEligibility.requireConfirmedAndActive(origen.getStudent());

        ProgramSectionView destino = sectionFinder.findOpenProgramSection(request.newSectionId());
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
        subjectRetakeGuard.requireNotAlreadyPassed(origen.getProgramEnrollment().getId(), origen.subjectId(), origen.getId());
        validatePrerequisites(origen.getProgramEnrollment(), origen.subjectId());
        validateEnrollmentWindow(destino.termId());
        if (countOccupiedSeats(destino.id()) >= capacityOf(destino)) {
            throw new SectionFullException(destino.id());
        }

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
            reactivada.setEnrolledAt(now);
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
        return subjectSectionEnrollmentRepository.findDetailedByProgramEnrollmentId(programEnrollmentId);
    }

    public List<SectionEnrollmentResponse> listByStudent(Long studentId) {
        List<SubjectSectionEnrollment> enrollments = subjectSectionEnrollmentRepository.findDetailedByStudentId(studentId);
        Map<Long, ProgramSubjectView> positions = resolveCurricularPositions(enrollments);
        return enrollments.stream()
                .map(enrollment -> {
                    ProgramSubjectView position = positions.get(enrollment.getId());
                    return SectionEnrollmentResponse.from(
                            enrollment,
                            position == null ? null : position.curricularYear(),
                            position == null ? null : position.curricularTerm())
                            .withFinalExamEligibility(finalExamEligibilityResolver.resolve(enrollment));
                })
                .toList();
    }

    private Map<Long, ProgramSubjectView> resolveCurricularPositions(List<SubjectSectionEnrollment> enrollments) {
        Map<Long, Map<Long, ProgramSubjectView>> subjectsByProgram = new HashMap<>();
        Map<Long, ProgramSubjectView> positions = new HashMap<>();
        for (SubjectSectionEnrollment enrollment : enrollments) {
            Long programId = enrollment.getProgramEnrollment().getProgram().getId();
            Map<Long, ProgramSubjectView> subjects = subjectsByProgram.computeIfAbsent(programId, id ->
                    programCurriculumFinder.findProgramSubjects(id).stream()
                            .collect(Collectors.toMap(ProgramSubjectView::subjectId, Function.identity(), (a, b) -> a)));
            ProgramSubjectView view = subjects.get(enrollment.subjectId());
            if (view != null) {
                positions.put(enrollment.getId(), view);
            }
        }
        return positions;
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
        waitlistPromoter.promoteNextFromWaitlist(sectionId);
    }

    private EnrollmentStatus calculateStatusByCapacity(ProgramSectionView section) {
        sectionFinder.lockProgramSection(section.id());
        return countOccupiedSeats(section.id()) < capacityOf(section)
                ? EnrollmentStatus.ENROLLED : EnrollmentStatus.WAITLISTED;
    }

    private int countOccupiedSeats(Long sectionId) {
        return (int) subjectSectionEnrollmentRepository
                .countBySubjectSectionIdAndStatusIn(sectionId,
                        List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS, EnrollmentStatus.REGULAR));
    }

    private int capacityOf(ProgramSectionView section) {
        return section.capacity() == null ? Integer.MAX_VALUE : section.capacity();
    }

    private void lockSectionsInAscendingOrder(Long firstSectionId, Long secondSectionId) {
        sectionFinder.lockProgramSection(Math.min(firstSectionId, secondSectionId));
        sectionFinder.lockProgramSection(Math.max(firstSectionId, secondSectionId));
    }

    private ProgramEnrollment findProgramEnrollmentOrThrow(Long id) {
        return programEnrollmentRepository.findById(id)
                .orElseThrow(() -> new ProgramEnrollmentNotFoundException(id));
    }

    private ProgramEnrollment lockProgramEnrollmentOrThrow(Long id) {
        return programEnrollmentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ProgramEnrollmentNotFoundException(id));
    }

    private Long findProgramEnrollmentIdOrThrow(Long sectionEnrollmentId) {
        return subjectSectionEnrollmentRepository.findProgramEnrollmentIdById(sectionEnrollmentId)
                .orElseThrow(() -> new SectionEnrollmentNotFoundException(sectionEnrollmentId));
    }

    private Long findSubjectSectionIdOrThrow(Long sectionEnrollmentId) {
        return subjectSectionEnrollmentRepository.findSubjectSectionIdById(sectionEnrollmentId)
                .orElseThrow(() -> new SectionEnrollmentNotFoundException(sectionEnrollmentId));
    }

    private void validatePrerequisites(ProgramEnrollment programEnrollment, Long subjectId) {
        prerequisiteEvaluator.assess(programEnrollment.getId(), subjectId).stream()
                .filter(assessment -> !assessment.met())
                .findFirst()
                .ifPresent(assessment -> {
                    throw new PrerequisiteNotMetException(
                            "No se cumple la correlatividad: " + assessment.subjectName());
                });
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

    private SubjectSectionEnrollment findOrThrow(Long id) {
        return subjectSectionEnrollmentRepository.findById(id)
                .orElseThrow(() -> new SectionEnrollmentNotFoundException(id));
    }
}
