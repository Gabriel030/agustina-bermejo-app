package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.enrollment.entity.ExceptionType;
import com.red_escuela.backend.enrollment.repository.DirectorApprovalRequestRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class LateExamApprovalChecker {

    private final DirectorApprovalRequestRepository directorApprovalRequestRepository;

    public LateExamApprovalChecker(DirectorApprovalRequestRepository directorApprovalRequestRepository) {
        this.directorApprovalRequestRepository = directorApprovalRequestRepository;
    }

    public boolean hasApprovedLateExam(Long programEnrollmentId, Long subjectId) {
        return directorApprovalRequestRepository.hasApproved(programEnrollmentId, subjectId, ExceptionType.LATE_EXAM);
    }
}
