package com.red_escuela.backend.repository;

import com.red_escuela.backend.entity.DirectorApprovalRequest;
import com.red_escuela.backend.entity.DirectorApprovalRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DirectorApprovalRequestRepository extends JpaRepository<DirectorApprovalRequest, Long> {

    List<DirectorApprovalRequest> findByProgramEnrollmentId(Long programEnrollmentId);

    List<DirectorApprovalRequest> findByStatus(DirectorApprovalRequestStatus status);
}
