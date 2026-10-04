package com.red_escuela.backend.enrollment.repository;

import com.red_escuela.backend.billing.entity.EnrollmentBillingStanding;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
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

    @Query("select e.id from ProgramEnrollment e where e.billingStanding = :standing and e.status in :statuses order by e.id")
    List<Long> findIdsByBillingStandingAndStatusIn(@Param("standing") EnrollmentBillingStanding standing,
                                                   @Param("statuses") Collection<ProgramEnrollmentStatus> statuses);

    @Query("select e.id from ProgramEnrollment e where e.program.id = :programId and e.status in :statuses order by e.id")
    List<Long> findIdsByProgramIdAndStatusIn(@Param("programId") Long programId, @Param("statuses") Collection<ProgramEnrollmentStatus> statuses);

    @Query("select distinct e.student.id from ProgramEnrollment e where e.status in :statuses")
    List<Long> findDistinctStudentIdsByStatusIn(@Param("statuses") Collection<ProgramEnrollmentStatus> statuses);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from ProgramEnrollment e where e.id = :id")
    java.util.Optional<ProgramEnrollment> findByIdForUpdate(@Param("id") Long id);
}
