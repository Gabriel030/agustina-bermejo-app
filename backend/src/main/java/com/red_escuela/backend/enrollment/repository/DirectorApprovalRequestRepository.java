package com.red_escuela.backend.enrollment.repository;

import com.red_escuela.backend.enrollment.entity.DirectorApprovalRequest;
import com.red_escuela.backend.enrollment.entity.DirectorApprovalRequestStatus;
import com.red_escuela.backend.enrollment.entity.ExceptionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DirectorApprovalRequestRepository extends JpaRepository<DirectorApprovalRequest, Long> {

    List<DirectorApprovalRequest> findByProgramEnrollmentId(Long programEnrollmentId);

    List<DirectorApprovalRequest> findByStatus(DirectorApprovalRequestStatus status);

    List<DirectorApprovalRequest> findByProgramEnrollmentIdAndTypeAndStatus(
            Long programEnrollmentId, ExceptionType type, DirectorApprovalRequestStatus status);

    boolean existsByProgramEnrollmentIdAndSubjectIdAndTypeAndStatus(
            Long programEnrollmentId, Long subjectId, ExceptionType type, DirectorApprovalRequestStatus status);

    default boolean hasApproved(Long programEnrollmentId, Long subjectId, ExceptionType type) {
        return existsByProgramEnrollmentIdAndSubjectIdAndTypeAndStatus(
                programEnrollmentId, subjectId, type, DirectorApprovalRequestStatus.APPROVED);
    }
}
