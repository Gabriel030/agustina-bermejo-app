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
import com.red_escuela.backend.attendance.service.AttendanceRiskCalculator;
import com.red_escuela.backend.evaluation.exception.StudentFreeByAbsencesException;
import com.red_escuela.backend.enrollment.repository.CourseSubjectSectionEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.DirectorApprovalRequestRepository;
import com.red_escuela.backend.entity.ExceptionType;
import com.red_escuela.backend.evaluation.repository.ExamBoardRegistrationRepository;
import com.red_escuela.backend.evaluation.repository.ExamBoardRepository;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ExamBoardRegistrationService {

    private final ExamBoardRegistrationRepository examBoardRegistrationRepository;
    private final ExamBoardRepository examBoardRepository;
    private final SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository;
    private final CourseSubjectSectionEnrollmentRepository courseSubjectSectionEnrollmentRepository;
    private final DirectorApprovalRequestRepository directorApprovalRequestRepository;
    private final AttendanceRiskCalculator attendanceRiskCalculator;

    public ExamBoardRegistrationService(
            ExamBoardRegistrationRepository examBoardRegistrationRepository,
            ExamBoardRepository examBoardRepository,
            SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository,
            CourseSubjectSectionEnrollmentRepository courseSubjectSectionEnrollmentRepository,
            DirectorApprovalRequestRepository directorApprovalRequestRepository,
            AttendanceRiskCalculator attendanceRiskCalculator) {
        this.examBoardRegistrationRepository = examBoardRegistrationRepository;
        this.examBoardRepository = examBoardRepository;
        this.subjectSectionEnrollmentRepository = subjectSectionEnrollmentRepository;
        this.courseSubjectSectionEnrollmentRepository = courseSubjectSectionEnrollmentRepository;
        this.directorApprovalRequestRepository = directorApprovalRequestRepository;
        this.attendanceRiskCalculator = attendanceRiskCalculator;
    }

    @Transactional
    public ExamBoardRegistration registerProgramEnrollment(Long examBoardId, Long enrollmentId) {
        ExamBoard examBoard = findExamBoardOrThrow(examBoardId);
        requireScheduledExamBoard(examBoard);

        SubjectSectionEnrollment enrollment = subjectSectionEnrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new SectionEnrollmentNotFoundException(enrollmentId));
        if (!directorApprovalRequestRepository.hasApproved(
                enrollment.getProgramEnrollment().getId(), enrollment.getSubjectSection().getSubject().getId(), ExceptionType.LATE_EXAM)) {
            requireOpenRegistrationWindow(examBoard);
        }
        if (enrollment.getStatus() != EnrollmentStatus.REGULAR) {
            throw new StudentNotRegularException(enrollmentId);
        }
        if (attendanceRiskCalculator.calculateForProgramEnrollment(enrollmentId).libre()) {
            throw new StudentFreeByAbsencesException(enrollmentId);
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
            registration.setStatus(ExamBoardRegistrationStatus.REGISTERED);
            registration.setRegisteredAt(LocalDateTime.now());
            return examBoardRegistrationRepository.save(registration);
        }

        ExamBoardRegistration created = new ExamBoardRegistration();
        created.setExamBoard(examBoard);
        created.setProgramSubjectSectionEnrollment(enrollment);
        created.setStatus(ExamBoardRegistrationStatus.REGISTERED);
        created.setRegisteredAt(LocalDateTime.now());
        try {
            return examBoardRegistrationRepository.saveAndFlush(created);
        } catch (DataIntegrityViolationException e) {
            throw new AlreadyRegisteredInExamBoardException(examBoardId, enrollmentId);
        }
    }

    @Transactional
    public ExamBoardRegistration registerCourseEnrollment(Long examBoardId, Long enrollmentId) {
        ExamBoard examBoard = findExamBoardOrThrow(examBoardId);
        requireOpenExamBoard(examBoard);

        CourseSubjectSectionEnrollment enrollment = courseSubjectSectionEnrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new SectionEnrollmentNotFoundException(enrollmentId));
        if (enrollment.getStatus() != EnrollmentStatus.REGULAR) {
            throw new StudentNotRegularException(enrollmentId);
        }
        if (attendanceRiskCalculator.calculateForCourseEnrollment(enrollmentId).libre()) {
            throw new StudentFreeByAbsencesException(enrollmentId);
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

        ExamBoardRegistration created = new ExamBoardRegistration();
        created.setExamBoard(examBoard);
        created.setCourseSubjectSectionEnrollment(enrollment);
        created.setStatus(ExamBoardRegistrationStatus.REGISTERED);
        created.setRegisteredAt(LocalDateTime.now());
        try {
            return examBoardRegistrationRepository.saveAndFlush(created);
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

    public ExamBoardRegistration get(Long id) {
        return examBoardRegistrationRepository.findById(id)
                .orElseThrow(() -> new ExamBoardRegistrationNotFoundException(id));
    }

    public List<ExamBoardRegistration> listByStudent(Long studentId) {
        return examBoardRegistrationRepository.findByProgramSubjectSectionEnrollment_ProgramEnrollment_Student_Id(studentId);
    }

    private ExamBoard findExamBoardOrThrow(Long examBoardId) {
        return examBoardRepository.findById(examBoardId)
                .orElseThrow(() -> new ExamBoardNotFoundException(examBoardId));
    }

    private void requireOpenExamBoard(ExamBoard examBoard) {
        requireScheduledExamBoard(examBoard);
        requireOpenRegistrationWindow(examBoard);
    }

    private void requireScheduledExamBoard(ExamBoard examBoard) {
        if (examBoard.getStatus() != ExamBoardStatus.SCHEDULED) {
            throw new ExamBoardClosedException(examBoard.getId());
        }
    }

    private void requireOpenRegistrationWindow(ExamBoard examBoard) {
        LocalDateTime now = LocalDateTime.now();
        if (examBoard.getRegistrationStart() != null && now.isBefore(examBoard.getRegistrationStart())) {
            throw new ExamBoardRegistrationWindowClosedException(examBoard.getId());
        }
        if (examBoard.getRegistrationEnd() != null && now.isAfter(examBoard.getRegistrationEnd())) {
            throw new ExamBoardRegistrationWindowClosedException(examBoard.getId());
        }
    }
}
