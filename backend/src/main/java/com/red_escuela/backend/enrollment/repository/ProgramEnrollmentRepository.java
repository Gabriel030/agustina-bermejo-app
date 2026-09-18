package com.red_escuela.backend.enrollment.repository;

import com.red_escuela.backend.billing.entity.EnrollmentBillingStanding;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProgramEnrollmentRepository extends JpaRepository<ProgramEnrollment, Long> {
    List<ProgramEnrollment> findByStudentId(Long studentId);

    List<ProgramEnrollment> findByStudentIdAndProgramIdAndStatusIn(
            Long studentId, Long programId, List<ProgramEnrollmentStatus> statuses);

    List<ProgramEnrollment> findByStatus(ProgramEnrollmentStatus status);

    List<ProgramEnrollment> findByStatusInAndIdGreaterThanOrderByIdAsc(List<ProgramEnrollmentStatus> statuses, Long lastId, Pageable pageable);

    List<ProgramEnrollment> findByStudentIdAndProgramIdAndStatusAndBillingStanding(
            Long studentId, Long programId, ProgramEnrollmentStatus status, EnrollmentBillingStanding billingStanding);
}
