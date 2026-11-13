package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.evaluation.domain.CourseworkOutcome;
import com.red_escuela.backend.evaluation.events.SubjectApprovalRevertedEvent;
import com.red_escuela.backend.identity.entity.Person;
import org.springframework.context.ApplicationEventPublisher;
import com.red_escuela.backend.evaluation.domain.PrerequisiteGatedOutcome;
import com.red_escuela.backend.evaluation.policy.CourseworkOutcomeCalculator;
import com.red_escuela.backend.evaluation.dto.CreateGradeItemRequest;
import com.red_escuela.backend.enrollment.api.ProgramPrerequisiteEvaluator;
import com.red_escuela.backend.enrollment.api.SectionEnrollmentFinder;
import com.red_escuela.backend.enrollment.api.SectionEnrollmentOutcomeRecorder;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.evaluation.entity.ExamBoard;
import com.red_escuela.backend.evaluation.entity.ExamBoardStatus;
import com.red_escuela.backend.enrollment.entity.FinalGradeType;
import com.red_escuela.backend.evaluation.entity.GradeItem;
import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.evaluation.exception.ExamBoardClosedException;
import com.red_escuela.backend.evaluation.exception.ExamBoardNotFoundException;
import com.red_escuela.backend.evaluation.exception.FinalGradeRequiresExamBoardException;
import com.red_escuela.backend.academicstructure.api.AcademicReferenceFinder;
import com.red_escuela.backend.evaluation.exception.GradeItemNotFoundException;
import com.red_escuela.backend.evaluation.exception.InvalidGradeItemWeightException;
import com.red_escuela.backend.evaluation.exception.NoEvaluationSchemeException;
import com.red_escuela.backend.academicstructure.exception.NotOwnSectionException;
import com.red_escuela.backend.academicstructure.api.SectionTeachingFinder;
import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.evaluation.repository.ExamBoardInstructorRepository;
import com.red_escuela.backend.evaluation.repository.ExamBoardRegistrationRepository;
import com.red_escuela.backend.evaluation.repository.ExamBoardRepository;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistration;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistrationStatus;
import com.red_escuela.backend.evaluation.repository.GradeItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.red_escuela.backend.shared.domain.Grade;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import com.red_escuela.backend.evaluation.api.GraduationRequestTriggerPort;

@Service
public class GradeItemRecorder {

    private final CourseworkOutcomeCalculator outcomeCalculator = new CourseworkOutcomeCalculator();

    private final GradeItemRepository gradeItemRepository;
    private final SectionEnrollmentFinder sectionEnrollmentFinder;
    private final SectionEnrollmentOutcomeRecorder sectionEnrollmentOutcomeRecorder;
    private final SectionTeachingFinder sectionTeachingFinder;
    private final ExamBoardRepository examBoardRepository;
    private final ExamBoardInstructorRepository examBoardInstructorRepository;
    private final ExamBoardRegistrationRepository examBoardRegistrationRepository;
    private final GraduationRequestTriggerPort graduationRequestTrigger;
    private final ApplicationEventPublisher eventPublisher;
    private final AbsenceFreeStatusChecker absenceFreeStatusChecker;
    private final FinalExamEligibilityEvaluator finalExamEligibilityEvaluator;
    private final FinalExamPrerequisiteChecker finalExamPrerequisiteChecker;
    private final GradeItemPublicationLockChecker publicationLock;
    private final PublishedGradeItemSelector publishedGradeItemSelector;
    private final FinalGradeRevertAuditor finalGradeRevertAuditor;
    private final AcademicReferenceFinder academicReferenceFinder;
    private final ExamBoardTakenChecker examBoardTakenChecker;
    private final CourseworkApprovalChecker courseworkApprovalChecker;
    private final ProgramPrerequisiteEvaluator programPrerequisiteEvaluator;

    public GradeItemRecorder(
            GradeItemRepository gradeItemRepository,
            SectionEnrollmentFinder sectionEnrollmentFinder,
            SectionEnrollmentOutcomeRecorder sectionEnrollmentOutcomeRecorder,
            SectionTeachingFinder sectionTeachingFinder,
            ExamBoardRepository examBoardRepository,
            ExamBoardInstructorRepository examBoardInstructorRepository,
            ExamBoardRegistrationRepository examBoardRegistrationRepository,
            GraduationRequestTriggerPort graduationRequestTrigger,
            ApplicationEventPublisher eventPublisher,
            AbsenceFreeStatusChecker absenceFreeStatusChecker,
            FinalExamEligibilityEvaluator finalExamEligibilityEvaluator,
            FinalExamPrerequisiteChecker finalExamPrerequisiteChecker,
            GradeItemPublicationLockChecker publicationLock,
            PublishedGradeItemSelector publishedGradeItemSelector,
            FinalGradeRevertAuditor finalGradeRevertAuditor,
            AcademicReferenceFinder academicReferenceFinder,
            ExamBoardTakenChecker examBoardTakenChecker,
            CourseworkApprovalChecker courseworkApprovalChecker,
            ProgramPrerequisiteEvaluator programPrerequisiteEvaluator) {
        this.gradeItemRepository = gradeItemRepository;
        this.sectionEnrollmentFinder = sectionEnrollmentFinder;
        this.sectionEnrollmentOutcomeRecorder = sectionEnrollmentOutcomeRecorder;
        this.sectionTeachingFinder = sectionTeachingFinder;
        this.examBoardRepository = examBoardRepository;
        this.examBoardInstructorRepository = examBoardInstructorRepository;
        this.examBoardRegistrationRepository = examBoardRegistrationRepository;
        this.graduationRequestTrigger = graduationRequestTrigger;
        this.eventPublisher = eventPublisher;
        this.absenceFreeStatusChecker = absenceFreeStatusChecker;
        this.finalExamEligibilityEvaluator = finalExamEligibilityEvaluator;
        this.finalExamPrerequisiteChecker = finalExamPrerequisiteChecker;
        this.publicationLock = publicationLock;
        this.publishedGradeItemSelector = publishedGradeItemSelector;
        this.finalGradeRevertAuditor = finalGradeRevertAuditor;
        this.academicReferenceFinder = academicReferenceFinder;
        this.examBoardTakenChecker = examBoardTakenChecker;
        this.courseworkApprovalChecker = courseworkApprovalChecker;
        this.programPrerequisiteEvaluator = programPrerequisiteEvaluator;
    }

    @Transactional
    public GradeItem loadProgramEnrollmentGrade(Long enrollmentId, Long instructorId, CreateGradeItemRequest request) {
        requireWeightBetweenZeroAndOne(request);
        requireFinalFromExamBoard(request);
        sectionEnrollmentFinder.lockProgramEnrollment(enrollmentId);
        SubjectSectionEnrollment enrollment = sectionEnrollmentFinder.findProgramEnrollment(enrollmentId);
        EvaluationScheme scheme = enrollment.getSubjectSection().getEvaluationScheme();
        if (scheme == null) {
            throw new NoEvaluationSchemeException(enrollment.getSubjectSection().getId());
        }
        ExamBoard examBoard = validateGradeSource(
                request.examBoardId(), instructorId,
                () -> sectionTeachingFinder
                        .teaches(SectionKind.PROGRAM, enrollment.getSubjectSection().getId(), instructorId),
                enrollment.getSubjectSection().getId());
        requireBoardCoversEnrollment(examBoard, request.type(), instructorId,
                examBoard != null && examBoard.getSubject() != null
                        && examBoard.getSubject().getId().equals(enrollment.getSubjectSection().getSubject().getId()),
                () -> examBoardRegistrationRepository.findByExamBoardIdAndProgramSubjectSectionEnrollmentId(
                        examBoard.getId(), enrollmentId));
        publicationLock.requireUnlocked(
                SectionKind.PROGRAM, enrollment.getSubjectSection().getId(), request.type(), request.sequenceNumber());
        if (request.type() == GradeItemType.EXAM) {
            courseworkApprovalChecker.requireApprovedBeforeExam(SectionKind.PROGRAM, enrollment.getSubjectSection().getId(),
                    scheme, gradeItemRepository.findByProgramSubjectSectionEnrollmentId(enrollmentId));
        }

        GradeItem gradeItem = gradeItemRepository
                .findByProgramSubjectSectionEnrollmentIdAndTypeAndSequenceNumber(
                        enrollmentId, request.type(), request.sequenceNumber())
                .orElseGet(GradeItem::new);
        boolean correction = isFinalCorrection(gradeItem, request);
        requireEligibleForFinalExam(request, enrollmentId, enrollment.hasRegularCoursework(), correction);
        requirePrerequisitesApprovedForFinalExam(request, enrollment, correction);
        auditFinalRevert(gradeItem, request, correction, instructorId, SectionKind.PROGRAM,
                enrollment.getSubjectSection().getId(), enrollmentId);
        gradeItem.setProgramSubjectSectionEnrollment(enrollment);
        applyData(gradeItem, request, examBoard);
        gradeItem = gradeItemRepository.save(gradeItem);
        applyProgramEnrollmentCondition(enrollment, correction, false);
        return gradeItem;
    }

    @Transactional
    public void clearProgramEnrollmentFinal(Long enrollmentId, Long instructorId, Long examBoardId, String reason) {
        sectionEnrollmentFinder.lockProgramEnrollment(enrollmentId);
        SubjectSectionEnrollment enrollment = sectionEnrollmentFinder.findProgramEnrollment(enrollmentId);
        Optional<GradeItem> existing = gradeItemRepository
                .findByExamBoardIdAndProgramSubjectSectionEnrollmentId(examBoardId, enrollmentId).stream()
                .filter(item -> item.getType() == GradeItemType.FINAL)
                .findFirst();
        if (existing.isEmpty()) {
            return;
        }
        revertFinalToAbsent(existing.get(), reason, instructorId, SectionKind.PROGRAM,
                enrollment.getSubjectSection().getId(), enrollmentId);
        applyProgramEnrollmentCondition(enrollment, true, false);
    }

    @Transactional
    public void clearCourseEnrollmentFinal(Long enrollmentId, Long instructorId, Long examBoardId, String reason) {
        sectionEnrollmentFinder.lockCourseEnrollment(enrollmentId);
        CourseSubjectSectionEnrollment enrollment = sectionEnrollmentFinder.findCourseEnrollment(enrollmentId);
        Optional<GradeItem> existing = gradeItemRepository
                .findByExamBoardIdAndCourseSubjectSectionEnrollmentId(examBoardId, enrollmentId).stream()
                .filter(item -> item.getType() == GradeItemType.FINAL)
                .findFirst();
        if (existing.isEmpty()) {
            return;
        }
        revertFinalToAbsent(existing.get(), reason, instructorId, SectionKind.COURSE,
                enrollment.getCourseSubjectSection().getId(), enrollmentId);
        applyCourseEnrollmentCondition(enrollment, true, false);
    }

    private void revertFinalToAbsent(GradeItem existing, String reason, Long instructorId, SectionKind kind,
                                     Long sectionId, Long enrollmentId) {
        ExamBoard board = existing.getExamBoard();
        if (board != null && board.getStatus() != ExamBoardStatus.SCHEDULED) {
            throw new ExamBoardClosedException(board.getId());
        }
        Long actorPersonId = academicReferenceFinder.findInstructor(instructorId).getPerson().getId();
        finalGradeRevertAuditor.recordRevert(existing, null, reason, kind, sectionId, enrollmentId, actorPersonId);
        gradeItemRepository.delete(existing);
        gradeItemRepository.flush();
    }

    @Transactional
    public void recalculateProgramEnrollmentCondition(Long enrollmentId) {
        sectionEnrollmentFinder.findProgramEnrollmentIfPresent(enrollmentId)
                .filter(enrollment -> enrollment.getSubjectSection().getEvaluationScheme() != null)
                .ifPresent(enrollment -> {
                    sectionEnrollmentFinder.lockProgramEnrollment(enrollmentId);
                    applyProgramEnrollmentCondition(enrollment, false, true);
                });
    }

    @Transactional
    public void recalculateCourseEnrollmentCondition(Long enrollmentId) {
        sectionEnrollmentFinder.findCourseEnrollmentIfPresent(enrollmentId)
                .filter(enrollment -> enrollment.getCourseSubjectSection().getEvaluationScheme() != null)
                .ifPresent(enrollment -> {
                    sectionEnrollmentFinder.lockCourseEnrollment(enrollmentId);
                    applyCourseEnrollmentCondition(enrollment, false, true);
                });
    }

    private void applyProgramEnrollmentCondition(
            SubjectSectionEnrollment enrollment, boolean finalCorrection, boolean rebuildFromItems) {
        Long enrollmentId = enrollment.getId();
        EvaluationScheme scheme = enrollment.getSubjectSection().getEvaluationScheme();
        List<GradeItem> items = publishedGradeItemSelector.selectPublished(
                SectionKind.PROGRAM, enrollment.getSubjectSection().getId(),
                gradeItemRepository.findByProgramSubjectSectionEnrollmentId(enrollmentId));
        Integer maxFinalExamAttempts = enrollment.getSubjectSection().getSubject().getMaxFinalExamAttempts();
        boolean rebuild = rebuildFromItems && isRebuildable(enrollment.getStatus(), enrollment.getFinalGradeType());
        var current = rebuild ? EnrollmentStatus.ENROLLED : enrollment.statusAssumingRegularWhen(finalCorrection);
        BooleanSupplier prerequisitesMet = () -> programPrerequisiteEvaluator.areAllApprovedOrWaivedOrSubjectOutsidePlan(
                enrollment.getProgramEnrollment().getId(), enrollment.subjectId());
        Long programEnrollmentId = enrollment.getProgramEnrollment().getId();
        boolean wasApproved = enrollment.getStatus() == EnrollmentStatus.APPROVED;
        if (rebuildFromItems && isApprovedByFinalExam(enrollment)) {
            PrerequisiteGatedOutcome gated = outcomeCalculator.promotionPolicy().restrictApprovalWhenPrerequisitesUnmet(
                    outcomeCalculator.calculate(scheme, items, EnrollmentStatus.REGULAR, maxFinalExamAttempts), prerequisitesMet);
            if (gated.forcedFinalCondition().isPresent()) {
                revertApprovalLosingPrerequisites(enrollment);
            }
            return;
        }
        Optional<CourseworkOutcome> calculated = outcomeCalculator.promotionPolicy().restrictApprovalWhenPrerequisitesUnmet(
                outcomeCalculator.calculate(scheme, items, current, maxFinalExamAttempts), prerequisitesMet)
                .outcome();
        CourseworkOutcome result = outcomeCalculator.regularityPolicy().holdWhileFree(
                calculated,
                current, () -> absenceFreeStatusChecker.isProgramEnrollmentFreeByAbsences(enrollmentId)).orElse(null);
        if (result == null && (finalCorrection || rebuild)) {
            result = new CourseworkOutcome(current, null, null);
        }
        if (result != null) {
            boolean losesApproval = wasApproved && result.status() != EnrollmentStatus.APPROVED;
            if (losesApproval && rebuildFromItems && graduationRequestTrigger.isGraduationFinalized(programEnrollmentId)) {
                graduationRequestTrigger.flagGraduationRequestOutdated(programEnrollmentId);
                return;
            }
            sectionEnrollmentOutcomeRecorder.recordProgramOutcome(
                    enrollmentId, result.status(), result.finalScore(), result.finalGradeType(), finalCorrection || rebuild);
            if (result.status() == EnrollmentStatus.APPROVED) {
                graduationRequestTrigger.requestGraduationIfEligible(programEnrollmentId);
            } else if (losesApproval) {
                graduationRequestTrigger.flagGraduationRequestOutdated(programEnrollmentId);
            }
        }
    }

    private void revertApprovalLosingPrerequisites(SubjectSectionEnrollment enrollment) {
        Long programEnrollmentId = enrollment.getProgramEnrollment().getId();
        if (graduationRequestTrigger.isGraduationFinalized(programEnrollmentId)) {
            graduationRequestTrigger.flagGraduationRequestOutdated(programEnrollmentId);
            return;
        }
        List<String> unmetNames = programPrerequisiteEvaluator
                .findUnmetForApprovalOrEmptyOutsidePlan(programEnrollmentId, enrollment.subjectId()).stream()
                .map(assessment -> assessment.subjectName())
                .toList();
        String unmet = String.join(", ", unmetNames);
        Optional<GradeItem> lastFinal = gradeItemRepository.findByProgramSubjectSectionEnrollmentId(enrollment.getId()).stream()
                .filter(item -> item.getType() == GradeItemType.FINAL)
                .max(Comparator.comparing(item -> item.getSequenceNumber() == null ? 0 : item.getSequenceNumber()));
        finalGradeRevertAuditor.recordSystemRevert(
                lastFinal.map(GradeItem::getId).orElse(null), lastFinal.map(GradeItem::getSequenceNumber).orElse(null),
                enrollment.getFinalScore(), "Sistema: correlativa sin aprobar (" + unmet + ")", SectionKind.PROGRAM,
                enrollment.getSubjectSection().getId(), enrollment.getId());
        sectionEnrollmentOutcomeRecorder.recordProgramOutcome(enrollment.getId(), EnrollmentStatus.REGULAR, null, null, true);
        graduationRequestTrigger.flagGraduationRequestOutdated(programEnrollmentId);
        Person student = enrollment.getStudent().getPerson();
        eventPublisher.publishEvent(SubjectApprovalRevertedEvent.revertedFor(
                student.getId(), enrollment.getSubjectSection().getSubject().getName(), unmetNames));
    }

    @Transactional
    public GradeItem loadCourseEnrollmentGrade(Long enrollmentId, Long instructorId, CreateGradeItemRequest request) {
        requireWeightBetweenZeroAndOne(request);
        requireFinalFromExamBoard(request);
        sectionEnrollmentFinder.lockCourseEnrollment(enrollmentId);
        CourseSubjectSectionEnrollment enrollment = sectionEnrollmentFinder.findCourseEnrollment(enrollmentId);
        EvaluationScheme scheme = enrollment.getCourseSubjectSection().getEvaluationScheme();
        if (scheme == null) {
            throw new NoEvaluationSchemeException(enrollment.getCourseSubjectSection().getId());
        }
        ExamBoard examBoard = validateGradeSource(
                request.examBoardId(), instructorId,
                () -> sectionTeachingFinder
                        .teaches(SectionKind.COURSE, enrollment.getCourseSubjectSection().getId(), instructorId),
                enrollment.getCourseSubjectSection().getId());
        requireBoardCoversEnrollment(examBoard, request.type(), instructorId,
                examBoard != null && examBoard.getCourseSubject() != null
                        && examBoard.getCourseSubject().getId()
                                .equals(enrollment.getCourseSubjectSection().getCourseSubject().getId()),
                () -> examBoardRegistrationRepository.findByExamBoardIdAndCourseSubjectSectionEnrollmentId(
                        examBoard.getId(), enrollmentId));
        publicationLock.requireUnlocked(
                SectionKind.COURSE, enrollment.getCourseSubjectSection().getId(), request.type(), request.sequenceNumber());
        if (request.type() == GradeItemType.EXAM) {
            courseworkApprovalChecker.requireApprovedBeforeExam(SectionKind.COURSE, enrollment.getCourseSubjectSection().getId(),
                    scheme, gradeItemRepository.findByCourseSubjectSectionEnrollmentId(enrollmentId));
        }

        GradeItem gradeItem = gradeItemRepository
                .findByCourseSubjectSectionEnrollmentIdAndTypeAndSequenceNumber(
                        enrollmentId, request.type(), request.sequenceNumber())
                .orElseGet(GradeItem::new);
        boolean correction = isFinalCorrection(gradeItem, request);
        requireEligibleForFinalExam(request, enrollmentId, enrollment.hasRegularCoursework(), correction);
        auditFinalRevert(gradeItem, request, correction, instructorId, SectionKind.COURSE,
                enrollment.getCourseSubjectSection().getId(), enrollmentId);
        gradeItem.setCourseSubjectSectionEnrollment(enrollment);
        applyData(gradeItem, request, examBoard);
        gradeItem = gradeItemRepository.save(gradeItem);
        applyCourseEnrollmentCondition(enrollment, correction, false);
        return gradeItem;
    }

    private void applyCourseEnrollmentCondition(
            CourseSubjectSectionEnrollment enrollment, boolean finalCorrection, boolean rebuildFromItems) {
        Long enrollmentId = enrollment.getId();
        EvaluationScheme scheme = enrollment.getCourseSubjectSection().getEvaluationScheme();
        List<GradeItem> items = publishedGradeItemSelector.selectPublished(
                SectionKind.COURSE, enrollment.getCourseSubjectSection().getId(),
                gradeItemRepository.findByCourseSubjectSectionEnrollmentId(enrollmentId));
        Integer maxFinalExamAttempts = enrollment.getCourseSubjectSection().getCourseSubject().getMaxFinalExamAttempts();
        boolean rebuild = rebuildFromItems && isRebuildable(enrollment.getStatus(), enrollment.getFinalGradeType());
        var current = rebuild ? EnrollmentStatus.ENROLLED : enrollment.statusAssumingRegularWhen(finalCorrection);
        CourseworkOutcome result = outcomeCalculator.regularityPolicy().holdWhileFree(
                outcomeCalculator.calculate(scheme, items, current, maxFinalExamAttempts),
                current, () -> absenceFreeStatusChecker.isCourseEnrollmentFreeByAbsences(enrollmentId)).orElse(null);
        if (result == null && (finalCorrection || rebuild)) {
            result = new CourseworkOutcome(current, null, null);
        }
        if (result != null) {
            sectionEnrollmentOutcomeRecorder.recordCourseOutcome(
                    enrollmentId, result.status(), result.finalScore(), result.finalGradeType(), finalCorrection || rebuild);
        }
    }

    private static boolean isRebuildable(EnrollmentStatus status, FinalGradeType finalGradeType) {
        return CourseworkOutcomeCalculator.isRebuildable(status, finalGradeType);
    }

    public List<GradeItem> listByProgramEnrollment(Long enrollmentId) {
        return gradeItemRepository.findByProgramSubjectSectionEnrollmentId(enrollmentId);
    }

    public List<GradeItem> listByCourseEnrollment(Long enrollmentId) {
        return gradeItemRepository.findByCourseSubjectSectionEnrollmentId(enrollmentId);
    }

    public GradeItem get(Long id) {
        return findOrThrow(id);
    }

    public Grade getExamBoardScore(Long examBoardId, Long enrollmentId) {
        return gradeItemRepository.findByExamBoardIdAndProgramSubjectSectionEnrollmentId(examBoardId, enrollmentId)
                .stream()
                .map(GradeItem::getScore)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private ExamBoard validateGradeSource(
            Long examBoardId, Long instructorId, java.util.function.BooleanSupplier esComisionPropia, Long sectionId) {
        if (examBoardId == null) {
            if (!esComisionPropia.getAsBoolean()) {
                throw new NotOwnSectionException(instructorId, sectionId);
            }
            return null;
        }
        ExamBoard examBoard = examBoardRepository.findById(examBoardId)
                .orElseThrow(() -> new ExamBoardNotFoundException(examBoardId));
        if (examBoard.getStatus() != com.red_escuela.backend.evaluation.entity.ExamBoardStatus.SCHEDULED) {
            throw new ExamBoardClosedException(examBoardId);
        }
        if (!examBoardInstructorRepository.existsByExamBoardIdAndInstructorId(examBoardId, instructorId)) {
            throw new NotOwnSectionException(instructorId, examBoardId);
        }
        examBoardTakenChecker.requireTaken(examBoard);
        return examBoard;
    }

    private void requireBoardCoversEnrollment(
            ExamBoard examBoard, GradeItemType type, Long instructorId, boolean sameSubject,
            java.util.function.Supplier<Optional<ExamBoardRegistration>> registration) {
        if (examBoard == null) {
            return;
        }
        boolean registered = sameSubject && registration.get()
                .filter(found -> found.getStatus() != ExamBoardRegistrationStatus.WITHDRAWN)
                .isPresent();
        if (type != GradeItemType.FINAL || !registered) {
            throw new NotOwnSectionException(instructorId, examBoard.getId());
        }
    }

    private void requireWeightBetweenZeroAndOne(CreateGradeItemRequest request) {
        Double weight = request.weight();
        if (weight != null && (weight.isNaN() || weight < 0.0 || weight > 1.0)) {
            throw new InvalidGradeItemWeightException(weight);
        }
    }

    private void requireFinalFromExamBoard(CreateGradeItemRequest request) {
        if (request.type() == GradeItemType.FINAL && request.examBoardId() == null) {
            throw new FinalGradeRequiresExamBoardException();
        }
    }

    private void auditFinalRevert(GradeItem existing, CreateGradeItemRequest request, boolean correction,
                                  Long instructorId, SectionKind kind, Long sectionId, Long enrollmentId) {
        if (!correction) {
            return;
        }
        ExamBoard originalBoard = existing.getExamBoard();
        if (originalBoard != null && originalBoard.getStatus() != ExamBoardStatus.SCHEDULED) {
            throw new ExamBoardClosedException(originalBoard.getId());
        }
        Grade newScore = Grade.of(request.score());
        if (newScore.equals(existing.getScore())) {
            return;
        }
        Long actorPersonId = academicReferenceFinder.findInstructor(instructorId).getPerson().getId();
        finalGradeRevertAuditor.recordRevert(existing, newScore, request.reason(), kind, sectionId, enrollmentId, actorPersonId);
    }

    private static boolean isApprovedByFinalExam(SubjectSectionEnrollment enrollment) {
        return enrollment.getStatus() == EnrollmentStatus.APPROVED && enrollment.getFinalGradeType() == FinalGradeType.FINAL_EXAM;
    }

    private boolean isFinalCorrection(GradeItem existing, CreateGradeItemRequest request) {
        return request.type() == GradeItemType.FINAL && existing.getId() != null;
    }

    private void requirePrerequisitesApprovedForFinalExam(
            CreateGradeItemRequest request, SubjectSectionEnrollment enrollment, boolean correction) {
        if (request.type() == GradeItemType.FINAL && !correction) {
            finalExamPrerequisiteChecker.requireAllApprovedOrWaived(
                    enrollment.getProgramEnrollment().getId(), enrollment.subjectId(), enrollment.getId());
        }
    }

    private void requireEligibleForFinalExam(CreateGradeItemRequest request, Long enrollmentId, boolean regular, boolean correction) {
        if (request.type() == GradeItemType.FINAL && !correction) {
            finalExamEligibilityEvaluator.requireEligible(enrollmentId, regular);
        }
    }

    private void applyData(GradeItem gradeItem, CreateGradeItemRequest request, ExamBoard examBoard) {
        gradeItem.setScore(Grade.of(request.score()));
        gradeItem.setType(request.type());
        gradeItem.setSequenceNumber(request.sequenceNumber());
        gradeItem.setWeight(request.weight());
        gradeItem.setDate(request.date());
        gradeItem.setComments(request.comments());
        gradeItem.setExamBoard(examBoard);
    }

    private GradeItem findOrThrow(Long id) {
        return gradeItemRepository.findById(id).orElseThrow(() -> new GradeItemNotFoundException(id));
    }
}
