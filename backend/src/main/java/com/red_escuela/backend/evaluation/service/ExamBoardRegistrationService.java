package com.red_escuela.backend.evaluation.service;

import com.red_escuela.backend.billing.planning.domain.EnrollmentFeeRequirementScope;
import com.red_escuela.backend.academicstructure.entity.Term;
import java.time.Clock;
import com.red_escuela.backend.billing.service.EnrollmentFeeRequirementService;
import com.red_escuela.backend.enrollment.api.LateExamApprovalChecker;
import com.red_escuela.backend.enrollment.api.SectionEnrollmentFinder;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.evaluation.entity.ExamBoard;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistration;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistrationStatus;
import com.red_escuela.backend.evaluation.entity.ExamBoardStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.evaluation.exception.AlreadyRegisteredInAnotherExamBoardException;
import com.red_escuela.backend.evaluation.exception.AlreadyRegisteredInExamBoardException;
import com.red_escuela.backend.evaluation.exception.ExamBoardClosedException;
import com.red_escuela.backend.evaluation.exception.ExamBoardNotFoundException;
import com.red_escuela.backend.evaluation.exception.ExamBoardRegistrationNotFoundException;
import com.red_escuela.backend.evaluation.exception.ExamBoardRegistrationNotWithdrawableException;
import com.red_escuela.backend.evaluation.exception.ExamBoardRegistrationWindowClosedException;
import com.red_escuela.backend.evaluation.exception.ExamBoardSubjectMismatchException;
import com.red_escuela.backend.evaluation.exception.MaxExamAttemptsExceededException;
import com.red_escuela.backend.evaluation.exception.StudentNotRegularException;
import com.red_escuela.backend.attendance.service.AttendanceRiskCalculator;
import com.red_escuela.backend.evaluation.exception.StudentFreeByAbsencesException;
import com.red_escuela.backend.evaluation.repository.ExamBoardRegistrationRepository;
import com.red_escuela.backend.evaluation.repository.ExamBoardRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ExamBoardRegistrationService {

    private final ExamBoardRegistrationRepository examBoardRegistrationRepository;
    private final ExamBoardRepository examBoardRepository;
    private final SectionEnrollmentFinder sectionEnrollmentFinder;
    private final LateExamApprovalChecker lateExamApprovalChecker;
    private final AttendanceRiskCalculator attendanceRiskCalculator;
    private final EnrollmentFeeRequirementService enrollmentFeeRequirementService;
    private final Clock clock;

    public ExamBoardRegistrationService(ExamBoardRegistrationRepository examBoardRegistrationRepository,
            ExamBoardRepository examBoardRepository,
            SectionEnrollmentFinder sectionEnrollmentFinder,
            LateExamApprovalChecker lateExamApprovalChecker,
            AttendanceRiskCalculator attendanceRiskCalculator,
            EnrollmentFeeRequirementService enrollmentFeeRequirementService,
            Clock clock) {
        this.enrollmentFeeRequirementService = enrollmentFeeRequirementService;
        this.examBoardRegistrationRepository = examBoardRegistrationRepository;
        this.examBoardRepository = examBoardRepository;
        this.sectionEnrollmentFinder = sectionEnrollmentFinder;
        this.lateExamApprovalChecker = lateExamApprovalChecker;
        this.attendanceRiskCalculator = attendanceRiskCalculator;
        this.clock = clock;
    }

    private void requireEnrollmentFeeOfCourseTerm(SubjectSectionEnrollment enrollment) {
        Term term = enrollment.getSubjectSection().getTerm();
        if (term == null) {
            enrollmentFeeRequirementService.requireForCurrentPeriod(enrollment.getProgramEnrollment(), EnrollmentFeeRequirementScope.EXAM_REGISTRATION);
            return;
        }
        enrollmentFeeRequirementService.requireForTerm(enrollment.getProgramEnrollment(), term, EnrollmentFeeRequirementScope.EXAM_REGISTRATION);
    }

    @Transactional
    public ExamBoardRegistration registerProgramEnrollmentOfStudent(Long studentId, Long examBoardId, Long enrollmentId) {
        sectionEnrollmentFinder.requireProgramEnrollmentOf(enrollmentId, studentId);
        return registerProgramEnrollment(examBoardId, enrollmentId);
    }

    @Transactional
    public ExamBoardRegistration registerProgramEnrollment(Long examBoardId, Long enrollmentId) {
        ExamBoard examBoard = findExamBoardOrThrow(examBoardId);
        requireScheduledExamBoard(examBoard);

        SubjectSectionEnrollment enrollment = sectionEnrollmentFinder.findProgramEnrollment(enrollmentId);
        requireEnrollmentFeeOfCourseTerm(enrollment);
        if (!lateExamApprovalChecker.hasApprovedLateExam(
                enrollment.getProgramEnrollment().getId(), enrollment.getSubjectSection().getSubject().getId())) {
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
        if (existing.isPresent() && existing.get().getStatus() != ExamBoardRegistrationStatus.WITHDRAWN) {
            throw new AlreadyRegisteredInExamBoardException(examBoardId, enrollmentId);
        }

        Integer maxAttempts = examBoard.getSubject().getMaxFinalExamAttempts();
        if (maxAttempts != null) {
            long previousAttempts = examBoardRegistrationRepository
                    .countByProgramSubjectSectionEnrollmentIdAndStatusNot(enrollmentId, ExamBoardRegistrationStatus.WITHDRAWN);
            if (previousAttempts >= maxAttempts) {
                throw new MaxExamAttemptsExceededException(subjectId, maxAttempts);
            }
        }

        if (existing.isPresent()) {
            ExamBoardRegistration registration = existing.get();
            registration.setStatus(ExamBoardRegistrationStatus.REGISTERED);
            registration.setRegisteredAt(LocalDateTime.now(clock));
            return examBoardRegistrationRepository.save(registration);
        }

        ExamBoardRegistration created = new ExamBoardRegistration();
        created.setExamBoard(examBoard);
        created.setProgramSubjectSectionEnrollment(enrollment);
        created.setStatus(ExamBoardRegistrationStatus.REGISTERED);
        created.setRegisteredAt(LocalDateTime.now(clock));
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

        CourseSubjectSectionEnrollment enrollment = sectionEnrollmentFinder.findCourseEnrollment(enrollmentId);
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

        examBoardRegistrationRepository.findByCourseSubjectSectionEnrollmentId(enrollmentId).stream()
                .filter(r -> r.getStatus() == ExamBoardRegistrationStatus.REGISTERED && !r.getExamBoard().getId().equals(examBoardId))
                .findFirst()
                .ifPresent(other -> {
                    throw new AlreadyRegisteredInAnotherExamBoardException(courseSubjectId, other.getExamBoard().getId());
                });

        var existing = examBoardRegistrationRepository
                .findByExamBoardIdAndCourseSubjectSectionEnrollmentId(examBoardId, enrollmentId);
        if (existing.isPresent() && existing.get().getStatus() != ExamBoardRegistrationStatus.WITHDRAWN) {
            throw new AlreadyRegisteredInExamBoardException(examBoardId, enrollmentId);
        }

        Integer maxAttempts = examBoard.getCourseSubject().getMaxFinalExamAttempts();
        if (maxAttempts != null) {
            long previousAttempts = examBoardRegistrationRepository
                    .countByCourseSubjectSectionEnrollmentIdAndStatusNot(enrollmentId, ExamBoardRegistrationStatus.WITHDRAWN);
            if (previousAttempts >= maxAttempts) {
                throw new MaxExamAttemptsExceededException(courseSubjectId, maxAttempts);
            }
        }

        if (existing.isPresent()) {
            ExamBoardRegistration registration = existing.get();
            registration.setStatus(ExamBoardRegistrationStatus.REGISTERED);
            registration.setRegisteredAt(LocalDateTime.now(clock));
            return examBoardRegistrationRepository.save(registration);
        }

        ExamBoardRegistration created = new ExamBoardRegistration();
        created.setExamBoard(examBoard);
        created.setCourseSubjectSectionEnrollment(enrollment);
        created.setStatus(ExamBoardRegistrationStatus.REGISTERED);
        created.setRegisteredAt(LocalDateTime.now(clock));
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
            throw new ExamBoardRegistrationNotWithdrawableException(
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
        LocalDateTime now = LocalDateTime.now(clock);
        if (examBoard.getRegistrationStart() != null && now.isBefore(examBoard.getRegistrationStart())) {
            throw new ExamBoardRegistrationWindowClosedException(examBoard.getId());
        }
        if (examBoard.getRegistrationEnd() != null && now.isAfter(examBoard.getRegistrationEnd())) {
            throw new ExamBoardRegistrationWindowClosedException(examBoard.getId());
        }
    }
}
