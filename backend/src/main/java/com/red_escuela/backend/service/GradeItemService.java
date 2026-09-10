package com.red_escuela.backend.service;

import com.red_escuela.backend.dto.CreateGradeItemRequest;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.entity.EvaluationScheme;
import com.red_escuela.backend.entity.ExamBoard;
import com.red_escuela.backend.entity.FinalGradeType;
import com.red_escuela.backend.entity.GradeAggregationMethod;
import com.red_escuela.backend.entity.GradeItem;
import com.red_escuela.backend.entity.GradeItemType;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.exception.ExamBoardClosedException;
import com.red_escuela.backend.exception.ExamBoardNotFoundException;
import com.red_escuela.backend.exception.GradeItemNotFoundException;
import com.red_escuela.backend.exception.NoEvaluationSchemeException;
import com.red_escuela.backend.exception.NotOwnSectionException;
import com.red_escuela.backend.enrollment.exception.SectionEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.repository.CourseSubjectSectionEnrollmentRepository;
import com.red_escuela.backend.academicstructure.repository.CourseSubjectSectionInstructorRepository;
import com.red_escuela.backend.repository.ExamBoardInstructorRepository;
import com.red_escuela.backend.repository.ExamBoardRepository;
import com.red_escuela.backend.repository.GradeItemRepository;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionInstructorRepository;
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
    public GradeItem cargarNotaProgramEnrollment(Long enrollmentId, Long instructorId, CreateGradeItemRequest request) {
        SubjectSectionEnrollment enrollment = subjectSectionEnrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new SectionEnrollmentNotFoundException(enrollmentId));
        EvaluationScheme scheme = enrollment.getSubjectSection().getEvaluationScheme();
        if (scheme == null) {
            throw new NoEvaluationSchemeException(enrollment.getSubjectSection().getId());
        }
        ExamBoard examBoard = validarOrigenNota(
                request.examBoardId(), instructorId,
                () -> subjectSectionInstructorRepository
                        .existsBySectionIdAndInstructorId(enrollment.getSubjectSection().getId(), instructorId),
                enrollment.getSubjectSection().getId());

        GradeItem gradeItem = gradeItemRepository
                .findByProgramSubjectSectionEnrollmentIdAndTypeAndSequenceNumber(
                        enrollmentId, request.type(), request.sequenceNumber())
                .orElseGet(GradeItem::new);
        gradeItem.setProgramSubjectSectionEnrollment(enrollment);
        aplicarDatos(gradeItem, request, examBoard);
        gradeItem = gradeItemRepository.save(gradeItem);

        List<GradeItem> items = gradeItemRepository.findByProgramSubjectSectionEnrollmentId(enrollmentId);
        Integer maxFinalExamAttempts = enrollment.getSubjectSection().getSubject().getMaxFinalExamAttempts();
        ResultadoCursada resultado = calcularResultado(scheme, items, enrollment.getStatus(), maxFinalExamAttempts);
        if (resultado != null) {
            enrollment.setStatus(resultado.status());
            if (resultado.finalScore() != null) {
                enrollment.setFinalScore(resultado.finalScore());
                enrollment.setFinalGradeType(resultado.finalGradeType());
            }
            subjectSectionEnrollmentRepository.save(enrollment);
            if (resultado.status() == EnrollmentStatus.APPROVED) {
                graduationRequestService.createGraduationRequestIfEligible(enrollment.getProgramEnrollment().getId());
            }
        }
        return gradeItem;
    }

    @Transactional
    public GradeItem cargarNotaCourseEnrollment(Long enrollmentId, Long instructorId, CreateGradeItemRequest request) {
        CourseSubjectSectionEnrollment enrollment = courseSubjectSectionEnrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new SectionEnrollmentNotFoundException(enrollmentId));
        EvaluationScheme scheme = enrollment.getCourseSubjectSection().getEvaluationScheme();
        if (scheme == null) {
            throw new NoEvaluationSchemeException(enrollment.getCourseSubjectSection().getId());
        }
        ExamBoard examBoard = validarOrigenNota(
                request.examBoardId(), instructorId,
                () -> courseSubjectSectionInstructorRepository
                        .existsBySectionIdAndInstructorId(enrollment.getCourseSubjectSection().getId(), instructorId),
                enrollment.getCourseSubjectSection().getId());

        GradeItem gradeItem = gradeItemRepository
                .findByCourseSubjectSectionEnrollmentIdAndTypeAndSequenceNumber(
                        enrollmentId, request.type(), request.sequenceNumber())
                .orElseGet(GradeItem::new);
        gradeItem.setCourseSubjectSectionEnrollment(enrollment);
        aplicarDatos(gradeItem, request, examBoard);
        gradeItem = gradeItemRepository.save(gradeItem);

        List<GradeItem> items = gradeItemRepository.findByCourseSubjectSectionEnrollmentId(enrollmentId);
        Integer maxFinalExamAttempts = enrollment.getCourseSubjectSection().getCourseSubject().getMaxFinalExamAttempts();
        ResultadoCursada resultado = calcularResultado(scheme, items, enrollment.getStatus(), maxFinalExamAttempts);
        if (resultado != null) {
            enrollment.setStatus(resultado.status());
            if (resultado.finalScore() != null) {
                enrollment.setFinalScore(resultado.finalScore());
                enrollment.setFinalGradeType(resultado.finalGradeType());
            }
            courseSubjectSectionEnrollmentRepository.save(enrollment);
        }
        return gradeItem;
    }

    public List<GradeItem> listarPorProgramEnrollment(Long enrollmentId) {
        return gradeItemRepository.findByProgramSubjectSectionEnrollmentId(enrollmentId);
    }

    public List<GradeItem> listarPorCourseEnrollment(Long enrollmentId) {
        return gradeItemRepository.findByCourseSubjectSectionEnrollmentId(enrollmentId);
    }

    public GradeItem verDetalle(Long id) {
        return findOrThrow(id);
    }

    // Nota que el alumno sacó en UNA mesa puntual, si ya rindió — null si todavía no cargaron
    // el FINAL de esa mesa. Usado por "mis inscripciones a mesas" (ver
    // ExamBoardRegistrationService.listarMisInscripciones): ExamBoardRegistrationStatus.GRADED
    // nunca se setea en el código actual, así que esto es la única fuente real.
    public Double obtenerNotaDeMesa(Long examBoardId, Long enrollmentId) {
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
    private ExamBoard validarOrigenNota(
            Long examBoardId, Long instructorId, java.util.function.BooleanSupplier esComisionPropia, Long sectionId) {
        if (examBoardId == null) {
            if (!esComisionPropia.getAsBoolean()) {
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

    private void aplicarDatos(GradeItem gradeItem, CreateGradeItemRequest request, ExamBoard examBoard) {
        gradeItem.setType(request.type());
        gradeItem.setSequenceNumber(request.sequenceNumber());
        gradeItem.setWeight(request.weight());
        gradeItem.setDate(request.date());
        gradeItem.setScore(request.score());
        gradeItem.setMaxScore(request.maxScore());
        gradeItem.setComments(request.comments());
        gradeItem.setExamBoard(examBoard);
    }

    private record ResultadoCursada(EnrollmentStatus status, Double finalScore, FinalGradeType finalGradeType) {
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
     * ({@link #calcularConFinal} / {@link #calcularSinFinal} según tenga final o no).
     */
    private ResultadoCursada calcularResultado(
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
        // Un esquema "solo final" (cantidadEsperada == 0) no tiene parciales que evaluar: sin
        // este guard, el tamaño de una lista vacía cumpliría trivialmente ">= 0" más abajo.
        boolean todosLosParcialesCargados = cantidadEsperada > 0
                && (examenes.size() + tps.size()) >= cantidadEsperada;

        if (todosLosParcialesCargados && tpCountExpected > 0 && scheme.getMinRegularGrade() != null
                && !todosAprobadosIndividualmente(tps, scheme.getMinRegularGrade())) {
            return new ResultadoCursada(EnrollmentStatus.FAILED, null, null);
        }

        ResultadoCursada promocion = evaluarPromocion(scheme, examenes, todosLosParcialesCargados, examCountExpected);
        if (promocion != null) {
            return promocion;
        }

        return Boolean.TRUE.equals(scheme.getHasFinal())
                ? calcularConFinal(scheme, examenes, todosLosParcialesCargados, ultimoFinal, finales.size(), estadoActual, maxFinalExamAttempts)
                : calcularSinFinal(scheme, examenes, tps, todosLosParcialesCargados);
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
    private ResultadoCursada evaluarPromocion(
            EvaluationScheme scheme, List<GradeItem> examenes, boolean todosLosParcialesCargados, int examCountExpected) {
        if (!todosLosParcialesCargados || examCountExpected == 0 || scheme.getPromotionMinGrade() == null) {
            return null;
        }
        if (!todosAprobadosIndividualmente(examenes, scheme.getPromotionMinGrade())) {
            return null;
        }
        double promedio = examenes.stream().mapToDouble(GradeItem::getScore).average().orElse(0.0);
        return new ResultadoCursada(EnrollmentStatus.APPROVED, promedio, FinalGradeType.PROMOTION);
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
    private ResultadoCursada calcularConFinal(
            EvaluationScheme scheme, List<GradeItem> examenes, boolean todosLosParcialesCargados,
            GradeItem ultimoFinal, int intentosFinalUsados, EnrollmentStatus estadoActual, Integer maxFinalExamAttempts) {
        boolean regularAlcanzado = estadoActual == EnrollmentStatus.REGULAR;

        if (todosLosParcialesCargados && scheme.getMinRegularGrade() != null) {
            double promedio = aplicarAgregacion(scheme.getAggregationMethod(), examenes);
            if (promedio < scheme.getMinRegularGrade()) {
                return new ResultadoCursada(EnrollmentStatus.FAILED, null, null);
            }
            regularAlcanzado = true;
        }

        if (ultimoFinal == null || scheme.getMinApprovalGrade() == null) {
            return regularAlcanzado && estadoActual != EnrollmentStatus.REGULAR
                    ? new ResultadoCursada(EnrollmentStatus.REGULAR, null, null)
                    : null;
        }

        if (ultimoFinal.getScore() != null && ultimoFinal.getScore() >= scheme.getMinApprovalGrade()) {
            return new ResultadoCursada(EnrollmentStatus.APPROVED, ultimoFinal.getScore(), FinalGradeType.FINAL_EXAM);
        }

        boolean quedanIntentos = maxFinalExamAttempts == null || intentosFinalUsados < maxFinalExamAttempts;
        if (quedanIntentos) {
            return regularAlcanzado && estadoActual != EnrollmentStatus.REGULAR
                    ? new ResultadoCursada(EnrollmentStatus.REGULAR, null, null)
                    : null;
        }
        return new ResultadoCursada(EnrollmentStatus.FAILED, null, null);
    }

    // Materia SIN instancia de final (EvaluationScheme.hasFinal = false, caso legacy/distinto
    // de "promocionable" — ver evaluarPromocion): no hay nada que rendir más allá de la
    // cursada, así que el promedio de parciales+TPs combinado se compara directo contra
    // minRegularGrade (desaprueba si no llega) y minApprovalGrade (aprueba si lo alcanza) —
    // mismo criterio que ya existía antes de esta feature, sin cambios de comportamiento.
    private ResultadoCursada calcularSinFinal(
            EvaluationScheme scheme, List<GradeItem> examenes, List<GradeItem> tps, boolean todosLosParcialesCargados) {
        if (!todosLosParcialesCargados) {
            return null;
        }
        List<GradeItem> todos = new ArrayList<>(examenes);
        todos.addAll(tps);

        if (scheme.getMinRegularGrade() != null) {
            double promedioRegular = aplicarAgregacion(scheme.getAggregationMethod(), todos);
            if (promedioRegular < scheme.getMinRegularGrade()) {
                return new ResultadoCursada(EnrollmentStatus.FAILED, null, null);
            }
        }
        if (scheme.getMinApprovalGrade() == null) {
            return null;
        }
        double promedio = aplicarAgregacion(scheme.getAggregationMethod(), todos);
        if (promedio >= scheme.getMinApprovalGrade()) {
            return new ResultadoCursada(EnrollmentStatus.APPROVED, promedio, FinalGradeType.PROMOTION);
        }
        return null;
    }

    private boolean todosAprobadosIndividualmente(List<GradeItem> items, double umbral) {
        return !items.isEmpty() && items.stream().allMatch(i -> i.getScore() != null && i.getScore() >= umbral);
    }

    // Las 4 estrategias se implementan con su significado literal: promedio simple, promedio
    // ponderado por GradeItem.weight, máximo puntaje entre intentos, último intento cargado
    // (por fecha) — sin ambigüedad real del doc funcional que justifique preguntar.
    private double aplicarAgregacion(GradeAggregationMethod method, List<GradeItem> parciales) {
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
