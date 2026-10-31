package com.red_escuela.backend.evaluation.service;

import java.util.function.BooleanSupplier;
import com.red_escuela.backend.evaluation.dto.CreateGradeItemRequest;
import com.red_escuela.backend.enrollment.api.SectionEnrollmentFinder;
import com.red_escuela.backend.enrollment.api.SectionEnrollmentOutcomeRecorder;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.evaluation.entity.ExamBoard;
import com.red_escuela.backend.enrollment.entity.FinalGradeType;
import com.red_escuela.backend.evaluation.entity.GradeAggregationMethod;
import com.red_escuela.backend.evaluation.entity.GradeItem;
import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.evaluation.exception.ExamBoardClosedException;
import com.red_escuela.backend.evaluation.exception.ExamBoardNotFoundException;
import com.red_escuela.backend.evaluation.exception.GradeItemNotFoundException;
import com.red_escuela.backend.evaluation.exception.NoEvaluationSchemeException;
import com.red_escuela.backend.academicstructure.exception.NotOwnSectionException;
import com.red_escuela.backend.academicstructure.api.SectionTeachingFinder;
import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.evaluation.repository.ExamBoardInstructorRepository;
import com.red_escuela.backend.evaluation.repository.ExamBoardRepository;
import com.red_escuela.backend.evaluation.repository.GradeItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.red_escuela.backend.shared.domain.Grade;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import com.red_escuela.backend.evaluation.api.GraduationRequestTriggerPort;

@Service
public class GradeItemService {

    private static final int WORKING_SCALE = 6;

    private final GradeItemRepository gradeItemRepository;
    private final SectionEnrollmentFinder sectionEnrollmentFinder;
    private final SectionEnrollmentOutcomeRecorder sectionEnrollmentOutcomeRecorder;
    private final SectionTeachingFinder sectionTeachingFinder;
    private final ExamBoardRepository examBoardRepository;
    private final ExamBoardInstructorRepository examBoardInstructorRepository;
    private final GraduationRequestTriggerPort graduationRequestTrigger;
    private final AbsenceFreeStatusChecker absenceFreeStatusChecker;
    private final FinalExamEligibilityEvaluator finalExamEligibilityEvaluator;

    public GradeItemService(
            GradeItemRepository gradeItemRepository,
            SectionEnrollmentFinder sectionEnrollmentFinder,
            SectionEnrollmentOutcomeRecorder sectionEnrollmentOutcomeRecorder,
            SectionTeachingFinder sectionTeachingFinder,
            ExamBoardRepository examBoardRepository,
            ExamBoardInstructorRepository examBoardInstructorRepository,
            GraduationRequestTriggerPort graduationRequestTrigger,
            AbsenceFreeStatusChecker absenceFreeStatusChecker,
            FinalExamEligibilityEvaluator finalExamEligibilityEvaluator) {
        this.gradeItemRepository = gradeItemRepository;
        this.sectionEnrollmentFinder = sectionEnrollmentFinder;
        this.sectionEnrollmentOutcomeRecorder = sectionEnrollmentOutcomeRecorder;
        this.sectionTeachingFinder = sectionTeachingFinder;
        this.examBoardRepository = examBoardRepository;
        this.examBoardInstructorRepository = examBoardInstructorRepository;
        this.graduationRequestTrigger = graduationRequestTrigger;
        this.absenceFreeStatusChecker = absenceFreeStatusChecker;
        this.finalExamEligibilityEvaluator = finalExamEligibilityEvaluator;
    }

    @Transactional
    public GradeItem loadProgramEnrollmentGrade(Long enrollmentId, Long instructorId, CreateGradeItemRequest request) {
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

        GradeItem gradeItem = gradeItemRepository
                .findByProgramSubjectSectionEnrollmentIdAndTypeAndSequenceNumber(
                        enrollmentId, request.type(), request.sequenceNumber())
                .orElseGet(GradeItem::new);
        boolean correction = isFinalCorrection(gradeItem, request);
        requireEligibleForFinalExam(request, enrollmentId, enrollment.isRegular(), correction);
        gradeItem.setProgramSubjectSectionEnrollment(enrollment);
        applyData(gradeItem, request, examBoard);
        gradeItem = gradeItemRepository.save(gradeItem);
        applyProgramEnrollmentCondition(enrollment, correction);
        return gradeItem;
    }

    @Transactional
    public void recalculateProgramEnrollmentCondition(Long enrollmentId) {
        sectionEnrollmentFinder.findProgramEnrollmentIfPresent(enrollmentId)
                .filter(enrollment -> enrollment.getSubjectSection().getEvaluationScheme() != null)
                .ifPresent(enrollment -> applyProgramEnrollmentCondition(enrollment, false));
    }

    @Transactional
    public void recalculateCourseEnrollmentCondition(Long enrollmentId) {
        sectionEnrollmentFinder.findCourseEnrollmentIfPresent(enrollmentId)
                .filter(enrollment -> enrollment.getCourseSubjectSection().getEvaluationScheme() != null)
                .ifPresent(enrollment -> applyCourseEnrollmentCondition(enrollment, false));
    }

    private void applyProgramEnrollmentCondition(SubjectSectionEnrollment enrollment, boolean finalCorrection) {
        Long enrollmentId = enrollment.getId();
        EvaluationScheme scheme = enrollment.getSubjectSection().getEvaluationScheme();
        List<GradeItem> items = gradeItemRepository.findByProgramSubjectSectionEnrollmentId(enrollmentId);
        Integer maxFinalExamAttempts = enrollment.getSubjectSection().getSubject().getMaxFinalExamAttempts();
        var current = enrollment.statusAssumingRegularWhen(finalCorrection);
        CourseworkResult result = holdWhileFree(calculateResult(scheme, items, current, maxFinalExamAttempts),
                current, () -> absenceFreeStatusChecker.isProgramEnrollmentFreeByAbsences(enrollmentId));
        if (result == null && finalCorrection) {
            result = new CourseworkResult(current, null, null);
        }
        if (result != null) {
            sectionEnrollmentOutcomeRecorder.recordProgramOutcome(
                    enrollmentId, result.status(), result.finalScore(), result.finalGradeType(), finalCorrection);
            if (result.status() == EnrollmentStatus.APPROVED) {
                graduationRequestTrigger.requestGraduationIfEligible(enrollment.getProgramEnrollment().getId());
            }
        }
    }

    @Transactional
    public GradeItem loadCourseEnrollmentGrade(Long enrollmentId, Long instructorId, CreateGradeItemRequest request) {
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

        GradeItem gradeItem = gradeItemRepository
                .findByCourseSubjectSectionEnrollmentIdAndTypeAndSequenceNumber(
                        enrollmentId, request.type(), request.sequenceNumber())
                .orElseGet(GradeItem::new);
        boolean correction = isFinalCorrection(gradeItem, request);
        requireEligibleForFinalExam(request, enrollmentId, enrollment.isRegular(), correction);
        gradeItem.setCourseSubjectSectionEnrollment(enrollment);
        applyData(gradeItem, request, examBoard);
        gradeItem = gradeItemRepository.save(gradeItem);
        applyCourseEnrollmentCondition(enrollment, correction);
        return gradeItem;
    }

    private void applyCourseEnrollmentCondition(CourseSubjectSectionEnrollment enrollment, boolean finalCorrection) {
        Long enrollmentId = enrollment.getId();
        EvaluationScheme scheme = enrollment.getCourseSubjectSection().getEvaluationScheme();
        List<GradeItem> items = gradeItemRepository.findByCourseSubjectSectionEnrollmentId(enrollmentId);
        Integer maxFinalExamAttempts = enrollment.getCourseSubjectSection().getCourseSubject().getMaxFinalExamAttempts();
        var current = enrollment.statusAssumingRegularWhen(finalCorrection);
        CourseworkResult result = holdWhileFree(calculateResult(scheme, items, current, maxFinalExamAttempts),
                current, () -> absenceFreeStatusChecker.isCourseEnrollmentFreeByAbsences(enrollmentId));
        if (result == null && finalCorrection) {
            result = new CourseworkResult(current, null, null);
        }
        if (result != null) {
            sectionEnrollmentOutcomeRecorder.recordCourseOutcome(
                    enrollmentId, result.status(), result.finalScore(), result.finalGradeType(), finalCorrection);
        }
    }

    private CourseworkResult holdWhileFree(CourseworkResult result, EnrollmentStatus currentStatus, BooleanSupplier isFree) {
        if (result == null || !result.passesCoursework() || currentStatus == EnrollmentStatus.REGULAR) {
            return result;
        }
        return isFree.getAsBoolean() ? null : result;
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
        if (examBoard.getStatus() == com.red_escuela.backend.evaluation.entity.ExamBoardStatus.CLOSED) {
            throw new ExamBoardClosedException(examBoardId);
        }
        if (!examBoardInstructorRepository.existsByExamBoardIdAndInstructorId(examBoardId, instructorId)) {
            throw new NotOwnSectionException(instructorId, examBoardId);
        }
        return examBoard;
    }

    private boolean isFinalCorrection(GradeItem existing, CreateGradeItemRequest request) {
        return request.type() == GradeItemType.FINAL && existing.getId() != null;
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

    private record CourseworkResult(EnrollmentStatus status, Grade finalScore, FinalGradeType finalGradeType) {
        boolean passesCoursework() {
            return status == EnrollmentStatus.REGULAR
                    || (status == EnrollmentStatus.APPROVED && finalGradeType == FinalGradeType.PROMOTION);
        }
    }

    private CourseworkResult calculateResult(
            EvaluationScheme scheme, List<GradeItem> items, EnrollmentStatus estadoActual, Integer maxFinalExamAttempts) {
        if (estadoActual != EnrollmentStatus.ENROLLED
                && estadoActual != EnrollmentStatus.IN_PROGRESS
                && estadoActual != EnrollmentStatus.REGULAR) {
            return null;
        }

        List<GradeItem> examenes = items.stream().filter(i -> i.getType() == GradeItemType.EXAM).toList();
        List<GradeItem> tps = items.stream().filter(i -> i.getType() == GradeItemType.TP).toList();
        List<GradeItem> finales = items.stream().filter(i -> i.getType() == GradeItemType.FINAL).toList();
        GradeItem ultimoFinal = finales.stream()
                .max(Comparator.comparing(i -> i.getSequenceNumber() == null ? 0 : i.getSequenceNumber()))
                .orElse(null);

        int examCountExpected = scheme.getExamCountExpected() == null ? 0 : scheme.getExamCountExpected();
        int tpCountExpected = scheme.getTpCountExpected() == null ? 0 : scheme.getTpCountExpected();
        int cantidadEsperada = examCountExpected + tpCountExpected;
        boolean todosLosParcialesCargados = cantidadEsperada > 0
                && (examenes.size() + tps.size()) >= cantidadEsperada;

        if (todosLosParcialesCargados && tpCountExpected > 0 && scheme.getMinRegularGrade() != null
                && !allApprovedIndividually(tps, scheme.getMinRegularGrade())) {
            return new CourseworkResult(EnrollmentStatus.FAILED, null, null);
        }

        CourseworkResult promocion = evaluatePromotion(scheme, examenes, todosLosParcialesCargados, examCountExpected);
        if (promocion != null) {
            return promocion;
        }

        return Boolean.TRUE.equals(scheme.getHasFinal())
                ? calculateWithFinal(scheme, examenes, todosLosParcialesCargados, ultimoFinal, finales.size(), estadoActual, maxFinalExamAttempts)
                : calculateWithoutFinal(scheme, examenes, tps, todosLosParcialesCargados);
    }

    private CourseworkResult evaluatePromotion(
            EvaluationScheme scheme, List<GradeItem> examenes, boolean todosLosParcialesCargados, int examCountExpected) {
        if (!todosLosParcialesCargados || examCountExpected == 0 || scheme.getPromotionMinGrade() == null) {
            return null;
        }
        if (!allApprovedIndividually(examenes, scheme.getPromotionMinGrade())) {
            return null;
        }
        Grade promedio = Grade.average(examenes.stream().map(GradeItem::getScore).toList()).orElseThrow();
        return new CourseworkResult(EnrollmentStatus.APPROVED, promedio, FinalGradeType.PROMOTION);
    }

    private CourseworkResult calculateWithFinal(
            EvaluationScheme scheme, List<GradeItem> examenes, boolean todosLosParcialesCargados,
            GradeItem ultimoFinal, int intentosFinalUsados, EnrollmentStatus estadoActual, Integer maxFinalExamAttempts) {
        boolean regularAlcanzado = estadoActual == EnrollmentStatus.REGULAR;

        if (todosLosParcialesCargados && scheme.getMinRegularGrade() != null) {
            Optional<Grade> promedio = applyAggregation(scheme.getAggregationMethod(), examenes);
            if (promedio.map(p -> !p.meets(scheme.getMinRegularGrade())).orElse(true)) {
                return new CourseworkResult(EnrollmentStatus.FAILED, null, null);
            }
            regularAlcanzado = true;
        }

        if (ultimoFinal == null || scheme.getMinApprovalGrade() == null) {
            return regularAlcanzado && estadoActual != EnrollmentStatus.REGULAR
                    ? new CourseworkResult(EnrollmentStatus.REGULAR, null, null)
                    : null;
        }

        if (ultimoFinal.getScore() != null && ultimoFinal.getScore().meets(scheme.getMinApprovalGrade())) {
            return new CourseworkResult(EnrollmentStatus.APPROVED, ultimoFinal.getScore(), FinalGradeType.FINAL_EXAM);
        }

        boolean quedanIntentos = maxFinalExamAttempts == null || intentosFinalUsados < maxFinalExamAttempts;
        if (quedanIntentos) {
            return regularAlcanzado && estadoActual != EnrollmentStatus.REGULAR
                    ? new CourseworkResult(EnrollmentStatus.REGULAR, null, null)
                    : null;
        }
        return new CourseworkResult(EnrollmentStatus.FAILED, null, null);
    }

    private CourseworkResult calculateWithoutFinal(
            EvaluationScheme scheme, List<GradeItem> examenes, List<GradeItem> tps, boolean todosLosParcialesCargados) {
        if (!todosLosParcialesCargados) {
            return null;
        }
        List<GradeItem> todos = new ArrayList<>(examenes);
        todos.addAll(tps);

        if (scheme.getMinRegularGrade() != null) {
            Optional<Grade> promedioRegular = applyAggregation(scheme.getAggregationMethod(), todos);
            if (promedioRegular.map(p -> !p.meets(scheme.getMinRegularGrade())).orElse(true)) {
                return new CourseworkResult(EnrollmentStatus.FAILED, null, null);
            }
        }
        if (scheme.getMinApprovalGrade() == null) {
            return null;
        }
        return applyAggregation(scheme.getAggregationMethod(), todos)
                .filter(promedio -> promedio.meets(scheme.getMinApprovalGrade()))
                .map(promedio -> new CourseworkResult(EnrollmentStatus.APPROVED, promedio, FinalGradeType.PROMOTION))
                .orElse(null);
    }

    private boolean allApprovedIndividually(List<GradeItem> items, Grade umbral) {
        return !items.isEmpty() && items.stream().allMatch(i -> i.getScore() != null && i.getScore().meets(umbral));
    }

    private Optional<Grade> applyAggregation(GradeAggregationMethod method, List<GradeItem> parciales) {
        List<Grade> scores = parciales.stream().map(GradeItem::getScore).filter(Objects::nonNull).toList();
        if (scores.isEmpty() || method == null) {
            return Optional.empty();
        }
        return switch (method) {
            case SIMPLE_AVERAGE -> Grade.average(scores);
            case WEIGHTED_AVERAGE -> {
                BigDecimal sumaPonderada = BigDecimal.ZERO;
                BigDecimal sumaPesos = BigDecimal.ZERO;
                for (GradeItem item : parciales) {
                    if (item.getScore() == null) {
                        continue;
                    }
                    BigDecimal peso = item.getWeight() == null ? BigDecimal.ZERO : BigDecimal.valueOf(item.getWeight());
                    sumaPonderada = sumaPonderada.add(item.getScore().value().multiply(peso));
                    sumaPesos = sumaPesos.add(peso);
                }
                yield sumaPesos.signum() == 0
                        ? Grade.average(scores)
                        : Optional.of(Grade.averageOf(sumaPonderada.divide(sumaPesos, WORKING_SCALE, RoundingMode.HALF_UP)));
            }
            case MAX_SCORE -> scores.stream().max(Comparator.naturalOrder());
            case LAST_ATTEMPT -> parciales.stream()
                    .filter(i -> i.getScore() != null)
                    .max(Comparator.comparing(GradeItem::getDate, Comparator.nullsFirst(Comparator.naturalOrder())))
                    .map(GradeItem::getScore);
        };
    }

    private GradeItem findOrThrow(Long id) {
        return gradeItemRepository.findById(id).orElseThrow(() -> new GradeItemNotFoundException(id));
    }
}
