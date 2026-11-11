package com.red_escuela.backend.enrollment.repository;

import com.red_escuela.backend.enrollment.entity.DirectorApprovalRequest;
import com.red_escuela.backend.enrollment.entity.DirectorApprovalRequestStatus;
import com.red_escuela.backend.enrollment.entity.ExceptionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface DirectorApprovalRequestRepository extends JpaRepository<DirectorApprovalRequest, Long> {

    List<DirectorApprovalRequest> findByProgramEnrollmentId(Long programEnrollmentId);

    List<DirectorApprovalRequest> findByStatus(DirectorApprovalRequestStatus status);

    List<DirectorApprovalRequest> findByProgramEnrollmentIdAndTypeAndStatus(
            Long programEnrollmentId, ExceptionType type, DirectorApprovalRequestStatus status);

    boolean existsByProgramEnrollmentIdAndSubjectIdAndTypeAndStatus(
            Long programEnrollmentId, Long subjectId, ExceptionType type, DirectorApprovalRequestStatus status);

    @Query("""
            select r.programEnrollment.id from DirectorApprovalRequest r
            where r.programEnrollment.id in :programEnrollmentIds and r.subject.id = :subjectId
            and r.type = :type and r.status = :status
            """)
    List<Long> findProgramEnrollmentIdsWithRequest(
            @Param("programEnrollmentIds") Collection<Long> programEnrollmentIds, @Param("subjectId") Long subjectId,
            @Param("type") ExceptionType type, @Param("status") DirectorApprovalRequestStatus status);

    default boolean hasApproved(Long programEnrollmentId, Long subjectId, ExceptionType type) {
        return existsByProgramEnrollmentIdAndSubjectIdAndTypeAndStatus(
                programEnrollmentId, subjectId, type, DirectorApprovalRequestStatus.APPROVED);
    }
}
