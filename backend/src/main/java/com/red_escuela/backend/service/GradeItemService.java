package com.red_escuela.backend.service;

import com.red_escuela.backend.dto.CreateGradeItemRequest;
import com.red_escuela.backend.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.entity.EnrollmentStatus;
import com.red_escuela.backend.entity.EvaluationScheme;
import com.red_escuela.backend.entity.ExamBoard;
import com.red_escuela.backend.entity.FinalGradeType;
import com.red_escuela.backend.entity.GradeAggregationMethod;
import com.red_escuela.backend.entity.GradeItem;
import com.red_escuela.backend.entity.GradeItemType;
import com.red_escuela.backend.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.exception.ExamBoardClosedException;
import com.red_escuela.backend.exception.ExamBoardNotFoundException;
import com.red_escuela.backend.exception.GradeItemNotFoundException;
import com.red_escuela.backend.exception.NoEvaluationSchemeException;
import com.red_escuela.backend.exception.NotOwnSectionException;
import com.red_escuela.backend.exception.SectionEnrollmentNotFoundException;
import com.red_escuela.backend.repository.CourseSubjectSectionEnrollmentRepository;
import com.red_escuela.backend.repository.CourseSubjectSectionInstructorRepository;
import com.red_escuela.backend.repository.ExamBoardInstructorRepository;
import com.red_escuela.backend.repository.ExamBoardRepository;
import com.red_escuela.backend.repository.GradeItemRepository;
import com.red_escuela.backend.repository.SubjectSectionEnrollmentRepository;
import com.red_escuela.backend.repository.SubjectSectionInstructorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * RF-DO-05/06/07, RF-AL-07: carga de notas respetando el EvaluationScheme de la comisión, y
 * recálculo automático del resultado de la cursada (decisión 6 de la spec técnica) — el
 * Docente nunca decide directamente si un alumno queda REGULAR/APPROVED/FAILED, eso es una
 * consecuencia calculada a partir de las notas cargadas.
 */
@Service
public class GradeItemService {

    private final GradeItemRepository gradeItemRepository;
    private final SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository;
    private final CourseSubjectSectionEnrollmentRepository courseSubjectSectionEnrollmentRepository;
    private final SubjectSectionInstructorRepository subjectSectionInstructorRepository;
    private final CourseSubjectSectionInstructorRepository courseSubjectSectionInstructorRepository;
    private final ExamBoardRepository examBoardRepository;
    private final ExamBoardInstructorRepository examBoardInstructorRepository;
    private final GraduationRequestService graduationRequestService;

    public GradeItemService(
            GradeItemRepository gradeItemRepository,
            SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository,
            CourseSubjectSectionEnrollmentRepository courseSubjectSectionEnrollmentRepository,
            SubjectSectionInstructorRepository subjectSectionInstructorRepository,
            CourseSubjectSectionInstructorRepository courseSubjectSectionInstructorRepository,
            ExamBoardRepository examBoardRepository,
            ExamBoardInstructorRepository examBoardInstructorRepository,
            GraduationRequestService graduationRequestService) {
        this.gradeItemRepository = gradeItemRepository;
        this.subjectSectionEnrollmentRepository = subjectSectionEnrollmentRepository;
        this.courseSubjectSectionEnrollmentRepository = courseSubjectSectionEnrollmentRepository;
        this.subjectSectionInstructorRepository = subjectSectionInstructorRepository;
        this.courseSubjectSectionInstructorRepository = courseSubjectSectionInstructorRepository;
        this.examBoardRepository = examBoardRepository;
        this.examBoardInstructorRepository = examBoardInstructorRepository;
        this.graduationRequestService = graduationRequestService;
    }

    @Transactional
    public GradeItem loadGradeProgramEnrollment(Long enrollmentId, Long instructorId, CreateGradeItemRequest request) {
        SubjectSectionEnrollment enrollment = subjectSectionEnrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new SectionEnrollmentNotFoundException(enrollmentId));
        EvaluationScheme scheme = enrollment.getSubjectSection().getEvaluationScheme();
        if (scheme == null) {
            throw new NoEvaluationSchemeException(enrollment.getSubjectSection().getId());
        }
        ExamBoard examBoard = validateGradeOrigin(
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
        EnrollmentResult result = calculateResult(scheme, items, enrollment.getStatus(), maxFinalExamAttempts);
        if (result != null) {
            enrollment.setStatus(result.status());
            if (result.finalScore() != null) {
                enrollment.setFinalScore(result.finalScore());
                enrollment.setFinalGradeType(result.finalGradeType());
            }
            subjectSectionEnrollmentRepository.save(enrollment);
            if (result.status() == EnrollmentStatus.APPROVED) {
                graduationRequestService.createGraduationRequestIfEligible(enrollment.getProgramEnrollment().getId());
            }
        }
        return gradeItem;
    }

    @Transactional
    public GradeItem loadGradeCourseEnrollment(Long enrollmentId, Long instructorId, CreateGradeItemRequest request) {
        CourseSubjectSectionEnrollment enrollment = courseSubjectSectionEnrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new SectionEnrollmentNotFoundException(enrollmentId));
        EvaluationScheme scheme = enrollment.getCourseSubjectSection().getEvaluationScheme();
        if (scheme == null) {
            throw new NoEvaluationSchemeException(enrollment.getCourseSubjectSection().getId());
        }
        ExamBoard examBoard = validateGradeOrigin(
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
        EnrollmentResult result = calculateResult(scheme, items, enrollment.getStatus(), maxFinalExamAttempts);
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

    public GradeItem viewDetail(Long id) {
        return findOrThrow(id);
    }

    // Nota que el alumno sacó en UNA mesa puntual, si ya rindió — null si todavía no cargaron
    // el FINAL de esa mesa. Usado por "mis inscripciones a mesas" (ver
    // ExamBoardRegistrationService.listMyEnrollments): ExamBoardRegistrationStatus.GRADED
    // nunca se setea en el código actual, así que esto es la única fuente real.
    public Double getExamBoardGrade(Long examBoardId, Long enrollmentId) {
        return gradeItemRepository.findByExamBoardIdAndProgramSubjectSectionEnrollmentId(examBoardId, enrollmentId)
                .stream()
                .map(GradeItem::getScore)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    // Si viene examBoardId: valida que la mesa exista, no esté cerrada, y que el instructor
    // integre su tribunal. Si no viene: valida "comisión propia" del Docente, igual que
    // AttendanceService.
    private ExamBoard validateGradeOrigin(
            Long examBoardId, Long instructorId, java.util.function.BooleanSupplier isOwnSection, Long sectionId) {
        if (examBoardId == null) {
            if (!isOwnSection.getAsBoolean()) {
                throw new NotOwnSectionException(instructorId, sectionId);
            }
            return null;
        }
        ExamBoard examBoard = examBoardRepository.findById(examBoardId)
                .orElseThrow(() -> new ExamBoardNotFoundException(examBoardId));
        if (examBoard.getStatus() == com.red_escuela.backend.entity.ExamBoardStatus.CLOSED) {
            throw new ExamBoardClosedException(examBoardId);
        }
        if (!examBoardInstructorRepository.existsByExamBoardIdAndInstructorId(examBoardId, instructorId)) {
            throw new NotOwnSectionException(instructorId, examBoardId);
        }
        return examBoard;
    }

    private void applyData(GradeItem gradeItem, CreateGradeItemRequest request, ExamBoard examBoard) {
        gradeItem.setType(request.type());
        gradeItem.setSequenceNumber(request.sequenceNumber());
        gradeItem.setWeight(request.weight());
        gradeItem.setDate(request.date());
        gradeItem.setScore(request.score());
        gradeItem.setMaxScore(request.maxScore());
        gradeItem.setComments(request.comments());
        gradeItem.setExamBoard(examBoard);
    }

    private record EnrollmentResult(EnrollmentStatus status, Double finalScore, FinalGradeType finalGradeType) {
    }

    /**
     * Algoritmo central (decisión 6): corre dentro de la misma transacción que el alta/edición
     * del GradeItem, leyendo el conjunto de notas más reciente vía la misma EntityManager —
     * necesario para no perder una carga concurrente de otra nota. Devuelve null si no hay
     * cambio de estado (cursada ya en estado terminal, o todavía no se completaron las
     * condiciones para recalcular). `maxFinalExamAttempts` viene de Subject/CourseSubject
     * (mismo campo que ya usa ExamBoardRegistrationService para topear cuántas mesas puede
     * pedir un alumno) — acá se usa para saber si un final desaprobado deja la cursada
     * directamente FAILED o si todavía le queda otro llamado disponible.
     * <p>
     * Los TPs son siempre un requisito de cursada, nunca se promedian con la nota final ni con
     * los parciales: tienen que estar aprobados INDIVIDUALMENTE (no alcanza con que el
     * promedio general los compense) para poder avanzar, sea cual sea el esquema — pedido
     * explícito del usuario ("si no se aprueban [los TPs] no permiten al alumno rendir el
     * final"). Ese gate se valida acá, antes de evaluar el atajo de promoción
     * ({@link #evaluarPromocion}) y, si no promocionó, el camino normal
     * ({@link #calculateWithFinalExam} / {@link #calculateWithoutFinalExam} según tenga final o no).
     */
    private EnrollmentResult calculateResult(
            EvaluationScheme scheme, List<GradeItem> items, EnrollmentStatus currentStatus, Integer maxFinalExamAttempts) {
        if (currentStatus != EnrollmentStatus.ENROLLED
                && currentStatus != EnrollmentStatus.IN_PROGRESS
                && currentStatus != EnrollmentStatus.REGULAR) {
            return null;
        }

        List<GradeItem> exams = items.stream().filter(i -> i.getType() == GradeItemType.EXAM).toList();
        List<GradeItem> tps = items.stream().filter(i -> i.getType() == GradeItemType.TP).toList();
        List<GradeItem> finals = items.stream().filter(i -> i.getType() == GradeItemType.FINAL).toList();
        GradeItem lastFinal = finals.stream()
                .max(Comparator.comparing(i -> i.getSequenceNumber() == null ? 0 : i.getSequenceNumber()))
                .orElse(null);

        int examCountExpected = scheme.getExamCountExpected() == null ? 0 : scheme.getExamCountExpected();
        int tpCountExpected = scheme.getTpCountExpected() == null ? 0 : scheme.getTpCountExpected();
        int expectedCount = examCountExpected + tpCountExpected;
        // Un esquema "solo final" (expectedCount == 0) no tiene parciales que evaluar: sin
        // este guard, el tamaño de una lista vacía cumpliría trivialmente ">= 0" más abajo.
        boolean allPartialsLoaded = expectedCount > 0
                && (exams.size() + tps.size()) >= expectedCount;

        if (allPartialsLoaded && tpCountExpected > 0 && scheme.getMinRegularGrade() != null
                && !allApprovedIndividually(tps, scheme.getMinRegularGrade())) {
            return new EnrollmentResult(EnrollmentStatus.FAILED, null, null);
        }

        EnrollmentResult promotionResult = evaluarPromocion(scheme, exams, allPartialsLoaded, examCountExpected);
        if (promotionResult != null) {
            return promotionResult;
        }

        return Boolean.TRUE.equals(scheme.getHasFinal())
                ? calculateWithFinalExam(scheme, exams, allPartialsLoaded, lastFinal, finals.size(), currentStatus, maxFinalExamAttempts)
                : calculateWithoutFinalExam(scheme, exams, tps, allPartialsLoaded);
    }

    // Atajo de promoción sin final — opcional (EvaluationScheme.promotionMinGrade nullable) e
    // INDEPENDIENTE de hasFinal: una materia puede tener final configurado y además ofrecer
    // promoción. Pedido explícito del usuario: "si ambos parciales son aprobados con una nota
    // igual o mayor a 7 entonces PROMOCIONADA... el alumno aprueba con una nota final que es
    // el promedio de las 2 notas" — cada examen tiene que llegar a esa nota INDIVIDUALMENTE
    // (no alcanza con que el promedio la alcance), y ese promedio final es el promedio simple
    // de los exámenes, no el GradeAggregationMethod configurado (ese sigue existiendo para
    // REGULAR/el camino con final). Si no promociona, devuelve null para que el llamador siga
    // el camino normal (Regular → final, o el esquema sin final de siempre).
    private EnrollmentResult evaluarPromocion(
            EvaluationScheme scheme, List<GradeItem> exams, boolean allPartialsLoaded, int examCountExpected) {
        if (!allPartialsLoaded || examCountExpected == 0 || scheme.getPromotionMinGrade() == null) {
            return null;
        }
        if (!allApprovedIndividually(exams, scheme.getPromotionMinGrade())) {
            return null;
        }
        double average = exams.stream().mapToDouble(GradeItem::getScore).average().orElse(0.0);
        return new EnrollmentResult(EnrollmentStatus.APPROVED, average, FinalGradeType.PROMOTION);
    }

    // Materia con final obligatorio (o que no llegó a promocionar): cursada completa
    // (parciales + TPs, gate de TPs ya validado) habilita REGULAR si el promedio de los
    // EXÁMENES (nunca los TPs, que son solo gate) llega a minRegularGrade; ya REGULAR, rendir
    // el FINAL con minApprovalGrade o más aprueba la materia — la nota final es la del examen
    // en sí, sin promediar con nada más (pedido explícito del usuario). Un final desaprobado
    // NO desaprueba la materia de una: si todavía quedan llamados disponibles
    // (maxFinalExamAttempts, o sin tope si es null), la cursada se queda en REGULAR esperando
    // que el alumno se anote a la próxima mesa (pedido explícito: "si no se aprueba el
    // primero, se tiene un segundo intento") — recién al agotar los llamados pasa a FAILED.
    private EnrollmentResult calculateWithFinalExam(
            EvaluationScheme scheme, List<GradeItem> exams, boolean allPartialsLoaded,
            GradeItem lastFinal, int finalAttemptsUsed, EnrollmentStatus currentStatus, Integer maxFinalExamAttempts) {
        boolean regularAchieved = currentStatus == EnrollmentStatus.REGULAR;

        if (allPartialsLoaded && scheme.getMinRegularGrade() != null) {
            double average = applyAggregation(scheme.getAggregationMethod(), exams);
            if (average < scheme.getMinRegularGrade()) {
                return new EnrollmentResult(EnrollmentStatus.FAILED, null, null);
            }
            regularAchieved = true;
        }

        if (lastFinal == null || scheme.getMinApprovalGrade() == null) {
            return regularAchieved && currentStatus != EnrollmentStatus.REGULAR
                    ? new EnrollmentResult(EnrollmentStatus.REGULAR, null, null)
                    : null;
        }

        if (lastFinal.getScore() != null && lastFinal.getScore() >= scheme.getMinApprovalGrade()) {
            return new EnrollmentResult(EnrollmentStatus.APPROVED, lastFinal.getScore(), FinalGradeType.FINAL_EXAM);
        }

        boolean attemptsRemaining = maxFinalExamAttempts == null || finalAttemptsUsed < maxFinalExamAttempts;
        if (attemptsRemaining) {
            return regularAchieved && currentStatus != EnrollmentStatus.REGULAR
                    ? new EnrollmentResult(EnrollmentStatus.REGULAR, null, null)
                    : null;
        }
        return new EnrollmentResult(EnrollmentStatus.FAILED, null, null);
    }

    // Materia SIN instancia de final (EvaluationScheme.hasFinal = false, caso legacy/distinto
    // de "promocionable" — ver evaluarPromocion): no hay nada que rendir más allá de la
    // cursada, así que el promedio de parciales+TPs combinado se compara directo contra
    // minRegularGrade (desaprueba si no llega) y minApprovalGrade (aprueba si lo alcanza) —
    // mismo criterio que ya existía antes de esta feature, sin cambios de comportamiento.
    private EnrollmentResult calculateWithoutFinalExam(
            EvaluationScheme scheme, List<GradeItem> exams, List<GradeItem> tps, boolean allPartialsLoaded) {
        if (!allPartialsLoaded) {
            return null;
        }
        List<GradeItem> allItems = new ArrayList<>(exams);
        allItems.addAll(tps);

        if (scheme.getMinRegularGrade() != null) {
            double regularAverage = applyAggregation(scheme.getAggregationMethod(), allItems);
            if (regularAverage < scheme.getMinRegularGrade()) {
                return new EnrollmentResult(EnrollmentStatus.FAILED, null, null);
            }
        }
        if (scheme.getMinApprovalGrade() == null) {
            return null;
        }
        double average = applyAggregation(scheme.getAggregationMethod(), allItems);
        if (average >= scheme.getMinApprovalGrade()) {
            return new EnrollmentResult(EnrollmentStatus.APPROVED, average, FinalGradeType.PROMOTION);
        }
        return null;
    }

    private boolean allApprovedIndividually(List<GradeItem> items, double threshold) {
        return !items.isEmpty() && items.stream().allMatch(i -> i.getScore() != null && i.getScore() >= threshold);
    }

    // Las 4 estrategias se implementan con su significado literal: promedio simple, promedio
    // ponderado por GradeItem.weight, máximo puntaje entre intentos, último intento cargado
    // (por fecha) — sin ambigüedad real del doc funcional que justifique preguntar.
    private double applyAggregation(GradeAggregationMethod method, List<GradeItem> gradeItems) {
        List<Double> scores = gradeItems.stream().map(GradeItem::getScore).filter(Objects::nonNull).toList();
        if (scores.isEmpty() || method == null) {
            return 0.0;
        }
        return switch (method) {
            case SIMPLE_AVERAGE -> scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            case WEIGHTED_AVERAGE -> {
                double weightedSum = 0.0;
                double totalWeight = 0.0;
                for (GradeItem item : gradeItems) {
                    if (item.getScore() == null) {
                        continue;
                    }
                    double weight = item.getWeight() == null ? 0.0 : item.getWeight();
                    weightedSum += item.getScore() * weight;
                    totalWeight += weight;
                }
                yield totalWeight == 0.0
                        ? scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0)
                        : weightedSum / totalWeight;
            }
            case MAX_SCORE -> scores.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
            case LAST_ATTEMPT -> gradeItems.stream()
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
