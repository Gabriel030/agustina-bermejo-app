package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.enrollment.entity.DirectorApprovalRequestStatus;
import com.red_escuela.backend.enrollment.entity.ExceptionType;
import com.red_escuela.backend.enrollment.repository.DirectorApprovalRequestRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Component
@Transactional(readOnly = true)
public class LateExamApprovalChecker {

    private final DirectorApprovalRequestRepository directorApprovalRequestRepository;
    private final Clock clock;

    public LateExamApprovalChecker(DirectorApprovalRequestRepository directorApprovalRequestRepository, Clock clock) {
        this.directorApprovalRequestRepository = directorApprovalRequestRepository;
        this.clock = clock;
    }

    public boolean hasApprovedLateExam(Long programEnrollmentId, Long subjectId) {
        return directorApprovalRequestRepository.hasUnconsumedApproved(programEnrollmentId, subjectId, ExceptionType.LATE_EXAM);
    }

    @Transactional
    public boolean consumeApprovedLateExam(Long programEnrollmentId, Long subjectId) {
        LocalDateTime now = LocalDateTime.now(clock);
        return directorApprovalRequestRepository
                .findByProgramEnrollmentIdAndSubjectIdAndTypeAndStatusAndConsumedAtIsNullOrderByDecidedAtAscIdAsc(
                        programEnrollmentId, subjectId, ExceptionType.LATE_EXAM, DirectorApprovalRequestStatus.APPROVED)
                .stream()
                .anyMatch(request -> directorApprovalRequestRepository.markConsumed(request.getId(), now) == 1);
    }
}
