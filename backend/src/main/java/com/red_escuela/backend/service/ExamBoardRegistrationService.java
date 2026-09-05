package com.red_escuela.backend.service;

import com.red_escuela.backend.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.entity.EnrollmentStatus;
import com.red_escuela.backend.entity.ExamBoard;
import com.red_escuela.backend.entity.ExamBoardRegistration;
import com.red_escuela.backend.entity.ExamBoardRegistrationStatus;
import com.red_escuela.backend.entity.ExamBoardStatus;
import com.red_escuela.backend.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.exception.AlreadyRegisteredInAnotherExamBoardException;
import com.red_escuela.backend.exception.AlreadyRegisteredInExamBoardException;
import com.red_escuela.backend.exception.CannotWithdrawException;
import com.red_escuela.backend.exception.ExamBoardClosedException;
import com.red_escuela.backend.exception.ExamBoardNotFoundException;
import com.red_escuela.backend.exception.ExamBoardRegistrationNotFoundException;
import com.red_escuela.backend.exception.ExamBoardRegistrationWindowClosedException;
import com.red_escuela.backend.exception.ExamBoardSubjectMismatchException;
import com.red_escuela.backend.exception.MaxExamAttemptsExceededException;
import com.red_escuela.backend.exception.SectionEnrollmentNotFoundException;
import com.red_escuela.backend.exception.StudentNotRegularException;
import com.red_escuela.backend.repository.CourseSubjectSectionEnrollmentRepository;
import com.red_escuela.backend.repository.ExamBoardRegistrationRepository;
import com.red_escuela.backend.repository.ExamBoardRepository;
import com.red_escuela.backend.repository.SubjectSectionEnrollmentRepository;
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
    public ExamBoardRegistration registerProgramEnrollment(Long examBoardId, Long enrollmentId) {
        ExamBoard examBoard = findExamBoardOrThrow(examBoardId);
        validateExamBoardOpen(examBoard);

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
            long previousAttempts = examBoardRegistrationRepository
                    .countByProgramSubjectSectionEnrollment_ProgramEnrollment_IdAndProgramSubjectSectionEnrollment_SubjectSection_Subject_IdAndStatusNot(
                            enrollment.getProgramEnrollment().getId(), subjectId, ExamBoardRegistrationStatus.WITHDRAWN);
            if (previousAttempts >= maxAttempts) {
                throw new MaxExamAttemptsExceededException(subjectId, maxAttempts);
            }
        }

        examBoardRegistrationRepository
                .findByProgramSubjectSectionEnrollment_ProgramEnrollment_IdAndProgramSubjectSectionEnrollment_SubjectSection_Subject_IdAndStatus(
                        enrollment.getProgramEnrollment().getId(), subjectId, ExamBoardRegistrationStatus.REGISTERED)
                .stream()
                .filter(r -> !r.getExamBoard().getId().equals(examBoardId))
                .findFirst()
                .ifPresent(other -> {
                    throw new AlreadyRegisteredInAnotherExamBoardException(subjectId, other.getExamBoard().getId());
                });

        var existing = examBoardRegistrationRepository
                .findByExamBoardIdAndProgramSubjectSectionEnrollmentId(examBoardId, enrollmentId);
        if (existing.isPresent()) {
            ExamBoardRegistration registration = existing.get();
            if (registration.getStatus() != ExamBoardRegistrationStatus.WITHDRAWN) {
                throw new AlreadyRegisteredInExamBoardException(examBoardId, enrollmentId);
            }
            // Reactiva la fila existente en vez de insertar una nueva (mismo criterio que
            // SubjectSectionEnrollmentService.enroll): el índice único parcial de la
            // migración V19 no discrimina por status.
            registration.setStatus(ExamBoardRegistrationStatus.REGISTERED);
            registration.setRegisteredAt(LocalDateTime.now());
            return examBoardRegistrationRepository.save(registration);
        }

        ExamBoardRegistration newRegistration = new ExamBoardRegistration();
        newRegistration.setExamBoard(examBoard);
        newRegistration.setProgramSubjectSectionEnrollment(enrollment);
        newRegistration.setStatus(ExamBoardRegistrationStatus.REGISTERED);
        newRegistration.setRegisteredAt(LocalDateTime.now());
        try {
            return examBoardRegistrationRepository.saveAndFlush(newRegistration);
        } catch (DataIntegrityViolationException e) {
            throw new AlreadyRegisteredInExamBoardException(examBoardId, enrollmentId);
        }
    }

    @Transactional
    public ExamBoardRegistration registerCourseEnrollment(Long examBoardId, Long enrollmentId) {
        ExamBoard examBoard = findExamBoardOrThrow(examBoardId);
        validateExamBoardOpen(examBoard);

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
            long previousAttempts = examBoardRegistrationRepository
                    .countByCourseSubjectSectionEnrollment_CourseEnrollment_IdAndCourseSubjectSectionEnrollment_CourseSubjectSection_CourseSubject_IdAndStatusNot(
                            enrollment.getCourseEnrollment().getId(), courseSubjectId, ExamBoardRegistrationStatus.WITHDRAWN);
            if (previousAttempts >= maxAttempts) {
                throw new MaxExamAttemptsExceededException(courseSubjectId, maxAttempts);
            }
        }

        examBoardRegistrationRepository.findByCourseSubjectSectionEnrollmentId(enrollmentId).stream()
                .filter(r -> r.getStatus() == ExamBoardRegistrationStatus.REGISTERED && !r.getExamBoard().getId().equals(examBoardId))
                .findFirst()
                .ifPresent(other -> {
                    throw new AlreadyRegisteredInAnotherExamBoardException(courseSubjectId, other.getExamBoard().getId());
                });

        var existing = examBoardRegistrationRepository
                .findByExamBoardIdAndCourseSubjectSectionEnrollmentId(examBoardId, enrollmentId);
        if (existing.isPresent()) {
            ExamBoardRegistration registration = existing.get();
            if (registration.getStatus() != ExamBoardRegistrationStatus.WITHDRAWN) {
                throw new AlreadyRegisteredInExamBoardException(examBoardId, enrollmentId);
            }
            registration.setStatus(ExamBoardRegistrationStatus.REGISTERED);
            registration.setRegisteredAt(LocalDateTime.now());
            return examBoardRegistrationRepository.save(registration);
        }

        ExamBoardRegistration newRegistration = new ExamBoardRegistration();
        newRegistration.setExamBoard(examBoard);
        newRegistration.setCourseSubjectSectionEnrollment(enrollment);
        newRegistration.setStatus(ExamBoardRegistrationStatus.REGISTERED);
        newRegistration.setRegisteredAt(LocalDateTime.now());
        try {
            return examBoardRegistrationRepository.saveAndFlush(newRegistration);
        } catch (DataIntegrityViolationException e) {
            throw new AlreadyRegisteredInExamBoardException(examBoardId, enrollmentId);
        }
    }

    @Transactional
    public void withdraw(Long registrationId) {
        ExamBoardRegistration registration = examBoardRegistrationRepository.findById(registrationId)
                .orElseThrow(() -> new ExamBoardRegistrationNotFoundException(registrationId));
        if (registration.getStatus() != ExamBoardRegistrationStatus.REGISTERED) {
            throw new CannotWithdrawException(
                    "La inscripción " + registrationId + " no está REGISTERED, no se puede dar de baja");
        }
        registration.setStatus(ExamBoardRegistrationStatus.WITHDRAWN);
        examBoardRegistrationRepository.save(registration);
    }

    public ExamBoardRegistration viewDetail(Long id) {
        return examBoardRegistrationRepository.findById(id)
                .orElseThrow(() -> new ExamBoardRegistrationNotFoundException(id));
    }

    // Todas las inscripciones a mesas de UN alumno (cualquier carrera, pasadas y presentes) —
    // para GET /me/exam-board-registrations. Alcance limitado al lado "program" (mismo
    // criterio que el resto de /me/exam-board-registrations, ver MeExamBoardRegistrationController).
    public List<ExamBoardRegistration> listMyEnrollments(Long studentId) {
        return examBoardRegistrationRepository.findByProgramSubjectSectionEnrollment_ProgramEnrollment_Student_Id(studentId);
    }

    private ExamBoard findExamBoardOrThrow(Long examBoardId) {
        return examBoardRepository.findById(examBoardId)
                .orElseThrow(() -> new ExamBoardNotFoundException(examBoardId));
    }

    private void validateExamBoardOpen(ExamBoard examBoard) {
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
