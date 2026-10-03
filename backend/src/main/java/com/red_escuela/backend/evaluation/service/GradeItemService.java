package com.red_escuela.backend.evaluation.service;

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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import com.red_escuela.backend.attendance.dto.AttendanceRiskResponse;
import com.red_escuela.backend.attendance.service.AttendanceRiskCalculator;
import com.red_escuela.backend.graduation.service.GraduationRequestAutoRequester;

@Service
public class GradeItemService {

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
        this.attendanceRiskCalculator = attendanceRiskCalculator;
        this.gradeItemRepository = gradeItemRepository;
        this.subjectSectionEnrollmentRepository = subjectSectionEnrollmentRepository;
        this.courseSubjectSectionEnrollmentRepository = courseSubjectSectionEnrollmentRepository;
        this.subjectSectionInstructorRepository = subjectSectionInstructorRepository;
        this.courseSubjectSectionInstructorRepository = courseSubjectSectionInstructorRepository;
        this.examBoardRepository = examBoardRepository;
        this.examBoardInstructorRepository = examBoardInstructorRepository;
        this.graduationRequestAutoRequester = graduationRequestAutoRequester;
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

        List<GradeItem> items = gradeItemRepository.findByProgramSubjectSectionEnrollmentId(enrollmentId);
        Integer maxFinalExamAttempts = enrollment.getSubjectSection().getSubject().getMaxFinalExamAttempts();
        CourseworkResult result = considerAttendance(
                calculateResult(scheme, items, enrollment.getStatus(), maxFinalExamAttempts), enrollment.getStatus(),
                () -> attendanceRiskCalculator.calculateForProgramEnrollment(enrollmentId));
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
        return gradeItem;
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

        List<GradeItem> items = gradeItemRepository.findByCourseSubjectSectionEnrollmentId(enrollmentId);
        Integer maxFinalExamAttempts = enrollment.getCourseSubjectSection().getCourseSubject().getMaxFinalExamAttempts();
        CourseworkResult result = considerAttendance(
                calculateResult(scheme, items, enrollment.getStatus(), maxFinalExamAttempts), enrollment.getStatus(),
                () -> attendanceRiskCalculator.calculateForCourseEnrollment(enrollmentId));
        if (result != null) {
            enrollment.setStatus(result.status());
            if (result.finalScore() != null) {
                enrollment.setFinalScore(result.finalScore());
                enrollment.setFinalGradeType(result.finalGradeType());
            }
            courseSubjectSectionEnrollmentRepository.save(enrollment);
        }
        return gradeItem;
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

    public Double getExamBoardScore(Long examBoardId, Long enrollmentId) {
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
        gradeItem.recordScore(request.score(), request.maxScore());
        gradeItem.setType(request.type());
        gradeItem.setSequenceNumber(request.sequenceNumber());
        gradeItem.setWeight(request.weight());
        gradeItem.setDate(request.date());
        gradeItem.setComments(request.comments());
        gradeItem.setExamBoard(examBoard);
    }

    private CourseworkResult considerAttendance(CourseworkResult result, EnrollmentStatus estadoActual,
                                                Supplier<AttendanceRiskResponse> attendanceRisk) {
        if (result == null || !result.passesCoursework() || estadoActual == EnrollmentStatus.REGULAR) {
            return result;
        }
        return attendanceRisk.get().libre() ? new CourseworkResult(EnrollmentStatus.FAILED, null, null) : result;
    }

    private record CourseworkResult(EnrollmentStatus status, Double finalScore, FinalGradeType finalGradeType) {
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
        double promedio = examenes.stream().mapToDouble(GradeItem::getScore).average().orElse(0.0);
        return new CourseworkResult(EnrollmentStatus.APPROVED, promedio, FinalGradeType.PROMOTION);
    }

    private CourseworkResult calculateWithFinal(
            EvaluationScheme scheme, List<GradeItem> examenes, boolean todosLosParcialesCargados,
            GradeItem ultimoFinal, int intentosFinalUsados, EnrollmentStatus estadoActual, Integer maxFinalExamAttempts) {
        boolean regularAlcanzado = estadoActual == EnrollmentStatus.REGULAR;

        if (todosLosParcialesCargados && scheme.getMinRegularGrade() != null) {
            double promedio = applyAggregation(scheme.getAggregationMethod(), examenes);
            if (promedio < scheme.getMinRegularGrade()) {
                return new CourseworkResult(EnrollmentStatus.FAILED, null, null);
            }
            regularAlcanzado = true;
        }

        if (ultimoFinal == null || scheme.getMinApprovalGrade() == null) {
            return regularAlcanzado && estadoActual != EnrollmentStatus.REGULAR
                    ? new CourseworkResult(EnrollmentStatus.REGULAR, null, null)
                    : null;
        }

        if (ultimoFinal.getScore() != null && ultimoFinal.getScore() >= scheme.getMinApprovalGrade()) {
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
            double promedioRegular = applyAggregation(scheme.getAggregationMethod(), todos);
            if (promedioRegular < scheme.getMinRegularGrade()) {
                return new CourseworkResult(EnrollmentStatus.FAILED, null, null);
            }
        }
        if (scheme.getMinApprovalGrade() == null) {
            return null;
        }
        double promedio = applyAggregation(scheme.getAggregationMethod(), todos);
        if (promedio >= scheme.getMinApprovalGrade()) {
            return new CourseworkResult(EnrollmentStatus.APPROVED, promedio, FinalGradeType.PROMOTION);
        }
        return null;
    }

    private boolean allApprovedIndividually(List<GradeItem> items, double umbral) {
        return !items.isEmpty() && items.stream().allMatch(i -> i.getScore() != null && i.getScore() >= umbral);
    }

    private double applyAggregation(GradeAggregationMethod method, List<GradeItem> parciales) {
        List<Double> scores = parciales.stream().map(GradeItem::getScore).filter(Objects::nonNull).toList();
        if (scores.isEmpty() || method == null) {
            return 0.0;
        }
        return switch (method) {
            case SIMPLE_AVERAGE -> scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            case WEIGHTED_AVERAGE -> {
                double sumaPonderada = 0.0;
                double sumaPesos = 0.0;
                for (GradeItem item : parciales) {
                    if (item.getScore() == null) {
                        continue;
                    }
                    double peso = item.getWeight() == null ? 0.0 : item.getWeight();
                    sumaPonderada += item.getScore() * peso;
                    sumaPesos += peso;
                }
                yield sumaPesos == 0.0
                        ? scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0)
                        : sumaPonderada / sumaPesos;
            }
            case MAX_SCORE -> scores.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
            case LAST_ATTEMPT -> parciales.stream()
                    .filter(i -> i.getScore() != null)
                    .max(Comparator.comparing(GradeItem::getDate, Comparator.nullsFirst(Comparator.naturalOrder())))
                    .map(GradeItem::getScore)
                    .orElse(0.0);
        };
    }

    private GradeItem findOrThrow(Long id) {
        return gradeItemRepository.findById(id).orElseThrow(() -> new GradeItemNotFoundException(id));
    }
}
