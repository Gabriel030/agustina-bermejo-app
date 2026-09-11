package com.red_escuela.backend.evaluation.service;

import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.evaluation.entity.ExamBoard;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistration;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistrationStatus;
import com.red_escuela.backend.evaluation.entity.ExamBoardStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.evaluation.exception.AlreadyRegisteredInAnotherExamBoardException;
import com.red_escuela.backend.evaluation.exception.AlreadyRegisteredInExamBoardException;
import com.red_escuela.backend.enrollment.exception.CannotWithdrawException;
import com.red_escuela.backend.evaluation.exception.ExamBoardClosedException;
import com.red_escuela.backend.evaluation.exception.ExamBoardNotFoundException;
import com.red_escuela.backend.evaluation.exception.ExamBoardRegistrationNotFoundException;
import com.red_escuela.backend.evaluation.exception.ExamBoardRegistrationWindowClosedException;
import com.red_escuela.backend.evaluation.exception.ExamBoardSubjectMismatchException;
import com.red_escuela.backend.evaluation.exception.MaxExamAttemptsExceededException;
import com.red_escuela.backend.enrollment.exception.SectionEnrollmentNotFoundException;
import com.red_escuela.backend.identity.exception.StudentNotRegularException;
import com.red_escuela.backend.enrollment.repository.CourseSubjectSectionEnrollmentRepository;
import com.red_escuela.backend.evaluation.repository.ExamBoardRegistrationRepository;
import com.red_escuela.backend.evaluation.repository.ExamBoardRepository;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * RF-AL-05/06: inscripción/baja de un alumno a una mesa de examen, validando regularidad
 * scopeada por carrera (decisión 8 de la spec técnica: se valida sobre la fila de inscripción
 * puntual, no sobre el Student, así el scope-por-carrera es automático).
 */
@Service
public class ExamBoardRegistrationService {

    private final ExamBoardRegistrationRepository examBoardRegistrationRepository;
    private final ExamBoardRepository examBoardRepository;
    private final SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository;
    private final CourseSubjectSectionEnrollmentRepository courseSubjectSectionEnrollmentRepository;

    public ExamBoardRegistrationService(
            ExamBoardRegistrationRepository examBoardRegistrationRepository,
            ExamBoardRepository examBoardRepository,
            SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository,
            CourseSubjectSectionEnrollmentRepository courseSubjectSectionEnrollmentRepository) {
        this.examBoardRegistrationRepository = examBoardRegistrationRepository;
        this.examBoardRepository = examBoardRepository;
        this.subjectSectionEnrollmentRepository = subjectSectionEnrollmentRepository;
        this.courseSubjectSectionEnrollmentRepository = courseSubjectSectionEnrollmentRepository;
    }

    @Transactional
    public ExamBoardRegistration inscribirProgramEnrollment(Long examBoardId, Long enrollmentId) {
        ExamBoard examBoard = findExamBoardOrThrow(examBoardId);
        validarMesaAbierta(examBoard);

        SubjectSectionEnrollment enrollment = subjectSectionEnrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new SectionEnrollmentNotFoundException(enrollmentId));
        if (enrollment.getStatus() != EnrollmentStatus.REGULAR) {
            throw new StudentNotRegularException(enrollmentId);
        }
        Long subjectId = enrollment.getSubjectSection().getSubject().getId();
        if (examBoard.getSubject() == null || !examBoard.getSubject().getId().equals(subjectId)) {
            throw new ExamBoardSubjectMismatchException(examBoardId, enrollmentId);
        }

        Integer maxAttempts = examBoard.getSubject().getMaxFinalExamAttempts();
        if (maxAttempts != null) {
            long intentosPrevios = examBoardRegistrationRepository
                    .countByProgramSubjectSectionEnrollment_ProgramEnrollment_IdAndProgramSubjectSectionEnrollment_SubjectSection_Subject_IdAndStatusNot(
                            enrollment.getProgramEnrollment().getId(), subjectId, ExamBoardRegistrationStatus.WITHDRAWN);
            if (intentosPrevios >= maxAttempts) {
                throw new MaxExamAttemptsExceededException(subjectId, maxAttempts);
            }
        }

        examBoardRegistrationRepository
                .findByProgramSubjectSectionEnrollment_ProgramEnrollment_IdAndProgramSubjectSectionEnrollment_SubjectSection_Subject_IdAndStatus(
                        enrollment.getProgramEnrollment().getId(), subjectId, ExamBoardRegistrationStatus.REGISTERED)
                .stream()
                .filter(r -> !r.getExamBoard().getId().equals(examBoardId))
                .findFirst()
                .ifPresent(otra -> {
                    throw new AlreadyRegisteredInAnotherExamBoardException(subjectId, otra.getExamBoard().getId());
                });

        var existente = examBoardRegistrationRepository
                .findByExamBoardIdAndProgramSubjectSectionEnrollmentId(examBoardId, enrollmentId);
        if (existente.isPresent()) {
            ExamBoardRegistration registration = existente.get();
            if (registration.getStatus() != ExamBoardRegistrationStatus.WITHDRAWN) {
                throw new AlreadyRegisteredInExamBoardException(examBoardId, enrollmentId);
            }
            // Reactiva la fila existente en vez de insertar una nueva (mismo criterio que
            // SubjectSectionEnrollmentService.inscribir): el índice único parcial de la
            // migración V19 no discrimina por status.
            registration.setStatus(ExamBoardRegistrationStatus.REGISTERED);
            registration.setRegisteredAt(LocalDateTime.now());
            return examBoardRegistrationRepository.save(registration);
        }

        ExamBoardRegistration nueva = new ExamBoardRegistration();
        nueva.setExamBoard(examBoard);
        nueva.setProgramSubjectSectionEnrollment(enrollment);
        nueva.setStatus(ExamBoardRegistrationStatus.REGISTERED);
        nueva.setRegisteredAt(LocalDateTime.now());
        try {
            return examBoardRegistrationRepository.saveAndFlush(nueva);
        } catch (DataIntegrityViolationException e) {
            throw new AlreadyRegisteredInExamBoardException(examBoardId, enrollmentId);
        }
    }

    @Transactional
    public ExamBoardRegistration inscribirCourseEnrollment(Long examBoardId, Long enrollmentId) {
        ExamBoard examBoard = findExamBoardOrThrow(examBoardId);
        validarMesaAbierta(examBoard);

        CourseSubjectSectionEnrollment enrollment = courseSubjectSectionEnrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new SectionEnrollmentNotFoundException(enrollmentId));
        if (enrollment.getStatus() != EnrollmentStatus.REGULAR) {
            throw new StudentNotRegularException(enrollmentId);
        }
        Long courseSubjectId = enrollment.getCourseSubjectSection().getCourseSubject().getId();
        if (examBoard.getCourseSubject() == null || !examBoard.getCourseSubject().getId().equals(courseSubjectId)) {
            throw new ExamBoardSubjectMismatchException(examBoardId, enrollmentId);
        }

        Integer maxAttempts = examBoard.getCourseSubject().getMaxFinalExamAttempts();
        if (maxAttempts != null) {
            long intentosPrevios = examBoardRegistrationRepository
                    .countByCourseSubjectSectionEnrollment_CourseEnrollment_IdAndCourseSubjectSectionEnrollment_CourseSubjectSection_CourseSubject_IdAndStatusNot(
                            enrollment.getCourseEnrollment().getId(), courseSubjectId, ExamBoardRegistrationStatus.WITHDRAWN);
            if (intentosPrevios >= maxAttempts) {
                throw new MaxExamAttemptsExceededException(courseSubjectId, maxAttempts);
            }
        }

        examBoardRegistrationRepository.findByCourseSubjectSectionEnrollmentId(enrollmentId).stream()
                .filter(r -> r.getStatus() == ExamBoardRegistrationStatus.REGISTERED && !r.getExamBoard().getId().equals(examBoardId))
                .findFirst()
                .ifPresent(otra -> {
                    throw new AlreadyRegisteredInAnotherExamBoardException(courseSubjectId, otra.getExamBoard().getId());
                });

        var existente = examBoardRegistrationRepository
                .findByExamBoardIdAndCourseSubjectSectionEnrollmentId(examBoardId, enrollmentId);
        if (existente.isPresent()) {
            ExamBoardRegistration registration = existente.get();
            if (registration.getStatus() != ExamBoardRegistrationStatus.WITHDRAWN) {
                throw new AlreadyRegisteredInExamBoardException(examBoardId, enrollmentId);
            }
            registration.setStatus(ExamBoardRegistrationStatus.REGISTERED);
            registration.setRegisteredAt(LocalDateTime.now());
            return examBoardRegistrationRepository.save(registration);
        }

        ExamBoardRegistration nueva = new ExamBoardRegistration();
        nueva.setExamBoard(examBoard);
        nueva.setCourseSubjectSectionEnrollment(enrollment);
        nueva.setStatus(ExamBoardRegistrationStatus.REGISTERED);
        nueva.setRegisteredAt(LocalDateTime.now());
        try {
            return examBoardRegistrationRepository.saveAndFlush(nueva);
        } catch (DataIntegrityViolationException e) {
            throw new AlreadyRegisteredInExamBoardException(examBoardId, enrollmentId);
        }
    }

    @Transactional
    public void darDeBaja(Long registrationId) {
        ExamBoardRegistration registration = examBoardRegistrationRepository.findById(registrationId)
                .orElseThrow(() -> new ExamBoardRegistrationNotFoundException(registrationId));
        if (registration.getStatus() != ExamBoardRegistrationStatus.REGISTERED) {
            throw new CannotWithdrawException(
                    "La inscripción " + registrationId + " no está REGISTERED, no se puede dar de baja");
        }
        registration.setStatus(ExamBoardRegistrationStatus.WITHDRAWN);
        examBoardRegistrationRepository.save(registration);
    }

    public ExamBoardRegistration verDetalle(Long id) {
        return examBoardRegistrationRepository.findById(id)
                .orElseThrow(() -> new ExamBoardRegistrationNotFoundException(id));
    }

    // Todas las inscripciones a mesas de UN alumno (cualquier carrera, pasadas y presentes) —
    // para GET /me/exam-board-registrations. Alcance limitado al lado "program" (mismo
    // criterio que el resto de /me/exam-board-registrations, ver MeExamBoardRegistrationController).
    public List<ExamBoardRegistration> listarMisInscripciones(Long studentId) {
        return examBoardRegistrationRepository.findByProgramSubjectSectionEnrollment_ProgramEnrollment_Student_Id(studentId);
    }

    private ExamBoard findExamBoardOrThrow(Long examBoardId) {
        return examBoardRepository.findById(examBoardId)
                .orElseThrow(() -> new ExamBoardNotFoundException(examBoardId));
    }

    private void validarMesaAbierta(ExamBoard examBoard) {
        if (examBoard.getStatus() != ExamBoardStatus.SCHEDULED) {
            throw new ExamBoardClosedException(examBoard.getId());
        }
        LocalDateTime now = LocalDateTime.now();
        if (examBoard.getRegistrationStart() != null && now.isBefore(examBoard.getRegistrationStart())) {
            throw new ExamBoardRegistrationWindowClosedException(examBoard.getId());
        }
        if (examBoard.getRegistrationEnd() != null && now.isAfter(examBoard.getRegistrationEnd())) {
            throw new ExamBoardRegistrationWindowClosedException(examBoard.getId());
        }
    }
}
