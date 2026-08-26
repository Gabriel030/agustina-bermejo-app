package com.red_escuela.backend.service;

import com.red_escuela.backend.dto.CreateGradeItemRequest;
import com.red_escuela.backend.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.entity.EnrollmentStatus;
import com.red_escuela.backend.entity.EvaluationScheme;
import com.red_escuela.backend.entity.ExamBoard;
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

    public GradeItemService(
            GradeItemRepository gradeItemRepository,
            SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository,
            CourseSubjectSectionEnrollmentRepository courseSubjectSectionEnrollmentRepository,
            SubjectSectionInstructorRepository subjectSectionInstructorRepository,
            CourseSubjectSectionInstructorRepository courseSubjectSectionInstructorRepository,
            ExamBoardRepository examBoardRepository,
            ExamBoardInstructorRepository examBoardInstructorRepository) {
        this.gradeItemRepository = gradeItemRepository;
        this.subjectSectionEnrollmentRepository = subjectSectionEnrollmentRepository;
        this.courseSubjectSectionEnrollmentRepository = courseSubjectSectionEnrollmentRepository;
        this.subjectSectionInstructorRepository = subjectSectionInstructorRepository;
        this.courseSubjectSectionInstructorRepository = courseSubjectSectionInstructorRepository;
        this.examBoardRepository = examBoardRepository;
        this.examBoardInstructorRepository = examBoardInstructorRepository;
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
        ResultadoCursada resultado = calcularResultado(scheme, items, enrollment.getStatus());
        if (resultado != null) {
            enrollment.setStatus(resultado.status());
            if (resultado.finalScore() != null) {
                enrollment.setFinalScore(resultado.finalScore());
                enrollment.setFinalGradeType(resultado.finalGradeType());
            }
            subjectSectionEnrollmentRepository.save(enrollment);
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
        ResultadoCursada resultado = calcularResultado(scheme, items, enrollment.getStatus());
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

    private record ResultadoCursada(EnrollmentStatus status, Double finalScore, String finalGradeType) {
    }

    /**
     * Algoritmo central (decisión 6): corre dentro de la misma transacción que el alta/edición
     * del GradeItem, leyendo el conjunto de notas más reciente vía la misma EntityManager —
     * necesario para no perder una carga concurrente de otra nota. Devuelve null si no hay
     * cambio de estado (cursada ya en estado terminal, o todavía no se completaron las
     * condiciones para recalcular).
     */
    private ResultadoCursada calcularResultado(EvaluationScheme scheme, List<GradeItem> items, EnrollmentStatus estadoActual) {
        if (estadoActual != EnrollmentStatus.ENROLLED
                && estadoActual != EnrollmentStatus.IN_PROGRESS
                && estadoActual != EnrollmentStatus.REGULAR) {
            return null;
        }

        List<GradeItem> parciales = items.stream()
                .filter(i -> i.getType() == GradeItemType.EXAM || i.getType() == GradeItemType.TP)
                .toList();
        GradeItem finalItem = items.stream()
                .filter(i -> i.getType() == GradeItemType.FINAL)
                .max(Comparator.comparing(i -> i.getSequenceNumber() == null ? 0 : i.getSequenceNumber()))
                .orElse(null);

        int examCountExpected = scheme.getExamCountExpected() == null ? 0 : scheme.getExamCountExpected();
        int tpCountExpected = scheme.getTpCountExpected() == null ? 0 : scheme.getTpCountExpected();
        int cantidadEsperada = examCountExpected + tpCountExpected;
        // Un esquema "solo final" (cantidadEsperada == 0) no tiene parciales que evaluar: sin
        // este guard, parciales.size() >= 0 da true trivialmente y el promedio de una lista
        // vacía se computaría como 0.0, reprobando la cursada sin que haya nada que reprobar.
        boolean todosLosParcialesCargados = cantidadEsperada > 0 && parciales.size() >= cantidadEsperada;

        EnrollmentStatus nuevoStatus = null;
        Double nuevoFinalScore = null;
        String nuevoFinalGradeType = null;

        if (todosLosParcialesCargados && scheme.getMinRegularGrade() != null) {
            double promedio = aplicarAgregacion(scheme.getAggregationMethod(), parciales);
            if (promedio < scheme.getMinRegularGrade()) {
                return new ResultadoCursada(EnrollmentStatus.FAILED, null, null);
            }
            if (estadoActual != EnrollmentStatus.REGULAR) {
                nuevoStatus = EnrollmentStatus.REGULAR;
            }
        }

        if (Boolean.TRUE.equals(scheme.getHasFinal()) && finalItem != null && scheme.getMinApprovalGrade() != null) {
            if (finalItem.getScore() != null && finalItem.getScore() >= scheme.getMinApprovalGrade()) {
                nuevoStatus = EnrollmentStatus.APPROVED;
                nuevoFinalScore = finalItem.getScore();
                nuevoFinalGradeType = finalItem.getType().name();
            } else {
                nuevoStatus = EnrollmentStatus.FAILED;
            }
        } else if (!Boolean.TRUE.equals(scheme.getHasFinal()) && todosLosParcialesCargados
                && scheme.getMinApprovalGrade() != null) {
            double promedio = aplicarAgregacion(scheme.getAggregationMethod(), parciales);
            if (promedio >= scheme.getMinApprovalGrade()) {
                nuevoStatus = EnrollmentStatus.APPROVED;
                nuevoFinalScore = promedio;
                nuevoFinalGradeType = "PROMOCION_DIRECTA";
            }
        }

        if (nuevoStatus == null) {
            return null;
        }
        return new ResultadoCursada(nuevoStatus, nuevoFinalScore, nuevoFinalGradeType);
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
