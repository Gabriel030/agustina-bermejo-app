package com.red_escuela.backend.evaluation.service;

import com.red_escuela.backend.attendance.service.AttendanceRiskCalculator;
import java.util.function.BooleanSupplier;
import com.red_escuela.backend.evaluation.dto.CreateGradeItemRequest;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.evaluation.entity.ExamBoard;
import com.red_escuela.backend.evaluation.entity.FinalGradeType;
import com.red_escuela.backend.evaluation.entity.GradeAggregationMethod;
import com.red_escuela.backend.evaluation.entity.GradeItem;
import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.evaluation.exception.ExamBoardClosedException;
import com.red_escuela.backend.evaluation.exception.ExamBoardNotFoundException;
import com.red_escuela.backend.evaluation.exception.GradeItemNotFoundException;
import com.red_escuela.backend.evaluation.exception.NoEvaluationSchemeException;
import com.red_escuela.backend.evaluation.exception.NotOwnSectionException;
import com.red_escuela.backend.enrollment.exception.SectionEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.repository.CourseSubjectSectionEnrollmentRepository;
import com.red_escuela.backend.academicstructure.repository.CourseSubjectSectionInstructorRepository;
import com.red_escuela.backend.evaluation.repository.ExamBoardInstructorRepository;
import com.red_escuela.backend.evaluation.repository.ExamBoardRepository;
import com.red_escuela.backend.evaluation.repository.GradeItemRepository;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionInstructorRepository;
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
import com.red_escuela.backend.graduation.service.GraduationRequestAutoRequester;

@Service
public class GradeItemService {

    private static final int WORKING_SCALE = 6;

    private final GradeItemRepository gradeItemRepository;
    private final SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository;
    private final CourseSubjectSectionEnrollmentRepository courseSubjectSectionEnrollmentRepository;
    private final SubjectSectionInstructorRepository subjectSectionInstructorRepository;
    private final CourseSubjectSectionInstructorRepository courseSubjectSectionInstructorRepository;
    private final ExamBoardRepository examBoardRepository;
    private final ExamBoardInstructorRepository examBoardInstructorRepository;
    private final GraduationRequestAutoRequester graduationRequestAutoRequester;
    private final AttendanceRiskCalculator attendanceRiskCalculator;

    public GradeItemService(
            GradeItemRepository gradeItemRepository,
            SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository,
            CourseSubjectSectionEnrollmentRepository courseSubjectSectionEnrollmentRepository,
            SubjectSectionInstructorRepository subjectSectionInstructorRepository,
            CourseSubjectSectionInstructorRepository courseSubjectSectionInstructorRepository,
            ExamBoardRepository examBoardRepository,
            ExamBoardInstructorRepository examBoardInstructorRepository,
            GraduationRequestAutoRequester graduationRequestAutoRequester,
            AttendanceRiskCalculator attendanceRiskCalculator) {
        this.gradeItemRepository = gradeItemRepository;
        this.subjectSectionEnrollmentRepository = subjectSectionEnrollmentRepository;
        this.courseSubjectSectionEnrollmentRepository = courseSubjectSectionEnrollmentRepository;
        this.subjectSectionInstructorRepository = subjectSectionInstructorRepository;
        this.courseSubjectSectionInstructorRepository = courseSubjectSectionInstructorRepository;
        this.examBoardRepository = examBoardRepository;
        this.examBoardInstructorRepository = examBoardInstructorRepository;
        this.graduationRequestAutoRequester = graduationRequestAutoRequester;
        this.attendanceRiskCalculator = attendanceRiskCalculator;
    }

    @Transactional
    public GradeItem loadProgramEnrollmentGrade(Long enrollmentId, Long instructorId, CreateGradeItemRequest request) {
        SubjectSectionEnrollment enrollment = subjectSectionEnrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new SectionEnrollmentNotFoundException(enrollmentId));
        EvaluationScheme scheme = enrollment.getSubjectSection().getEvaluationScheme();
        if (scheme == null) {
            throw new NoEvaluationSchemeException(enrollment.getSubjectSection().getId());
        }
        ExamBoard examBoard = validateGradeSource(
                request.examBoardId(), instructorId,
                () -> subjectSectionInstructorRepository
                        .existsBySectionIdAndInstructorId(enrollment.getSubjectSection().getId(), instructorId),
                enrollment.getSubjectSection().getId());

        GradeItem gradeItem = gradeItemRepository
                .findByProgramSubjectSectionEnrollmentIdAndTypeAndSequenceNumber(
                        enrollmentId, request.type(), request.sequenceNumber())
                .orElseGet(GradeItem::new);
        gradeItem.setProgramSubjectSectionEnrollment(enrollment);
        applyData(gradeItem, request, examBoard);
        gradeItem = gradeItemRepository.save(gradeItem);
        applyProgramEnrollmentCondition(enrollment);
        return gradeItem;
    }

    @Transactional
    public void recalculateProgramEnrollmentCondition(Long enrollmentId) {
        subjectSectionEnrollmentRepository.findById(enrollmentId)
                .filter(enrollment -> enrollment.getSubjectSection().getEvaluationScheme() != null)
                .ifPresent(this::applyProgramEnrollmentCondition);
    }

    @Transactional
    public void recalculateCourseEnrollmentCondition(Long enrollmentId) {
        courseSubjectSectionEnrollmentRepository.findById(enrollmentId)
                .filter(enrollment -> enrollment.getCourseSubjectSection().getEvaluationScheme() != null)
                .ifPresent(this::applyCourseEnrollmentCondition);
    }

    private void applyProgramEnrollmentCondition(SubjectSectionEnrollment enrollment) {
        Long enrollmentId = enrollment.getId();
        EvaluationScheme scheme = enrollment.getSubjectSection().getEvaluationScheme();
        List<GradeItem> items = gradeItemRepository.findByProgramSubjectSectionEnrollmentId(enrollmentId);
        Integer maxFinalExamAttempts = enrollment.getSubjectSection().getSubject().getMaxFinalExamAttempts();
        CourseworkResult result = holdWhileFree(calculateResult(scheme, items, enrollment.getStatus(), maxFinalExamAttempts),
                enrollment.getStatus(), () -> attendanceRiskCalculator.calculateForProgramEnrollment(enrollmentId).libre());
        if (result != null) {
            enrollment.setStatus(result.status());
            if (result.finalScore() != null) {
                enrollment.setFinalScore(result.finalScore());
                enrollment.setFinalGradeType(result.finalGradeType());
            }
            subjectSectionEnrollmentRepository.save(enrollment);
            if (result.status() == EnrollmentStatus.APPROVED) {
                graduationRequestAutoRequester.createIfEligible(enrollment.getProgramEnrollment().getId());
            }
        }
    }

    @Transactional
    public GradeItem loadCourseEnrollmentGrade(Long enrollmentId, Long instructorId, CreateGradeItemRequest request) {
        CourseSubjectSectionEnrollment enrollment = courseSubjectSectionEnrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new SectionEnrollmentNotFoundException(enrollmentId));
        EvaluationScheme scheme = enrollment.getCourseSubjectSection().getEvaluationScheme();
        if (scheme == null) {
            throw new NoEvaluationSchemeException(enrollment.getCourseSubjectSection().getId());
        }
        ExamBoard examBoard = validateGradeSource(
                request.examBoardId(), instructorId,
                () -> courseSubjectSectionInstructorRepository
                        .existsBySectionIdAndInstructorId(enrollment.getCourseSubjectSection().getId(), instructorId),
                enrollment.getCourseSubjectSection().getId());

        GradeItem gradeItem = gradeItemRepository
                .findByCourseSubjectSectionEnrollmentIdAndTypeAndSequenceNumber(
                        enrollmentId, request.type(), request.sequenceNumber())
                .orElseGet(GradeItem::new);
        gradeItem.setCourseSubjectSectionEnrollment(enrollment);
        applyData(gradeItem, request, examBoard);
        gradeItem = gradeItemRepository.save(gradeItem);
        applyCourseEnrollmentCondition(enrollment);
        return gradeItem;
    }

    private void applyCourseEnrollmentCondition(CourseSubjectSectionEnrollment enrollment) {
        Long enrollmentId = enrollment.getId();
        EvaluationScheme scheme = enrollment.getCourseSubjectSection().getEvaluationScheme();
        List<GradeItem> items = gradeItemRepository.findByCourseSubjectSectionEnrollmentId(enrollmentId);
        Integer maxFinalExamAttempts = enrollment.getCourseSubjectSection().getCourseSubject().getMaxFinalExamAttempts();
        CourseworkResult result = holdWhileFree(calculateResult(scheme, items, enrollment.getStatus(), maxFinalExamAttempts),
                enrollment.getStatus(), () -> attendanceRiskCalculator.calculateForCourseEnrollment(enrollmentId).libre());
        if (result != null) {
            enrollment.setStatus(result.status());
            if (result.finalScore() != null) {
                enrollment.setFinalScore(result.finalScore());
                enrollment.setFinalGradeType(result.finalGradeType());
            }
            courseSubjectSectionEnrollmentRepository.save(enrollment);
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
