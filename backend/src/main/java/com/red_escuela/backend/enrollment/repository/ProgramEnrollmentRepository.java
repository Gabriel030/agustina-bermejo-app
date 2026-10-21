package com.red_escuela.backend.enrollment.repository;

import com.red_escuela.backend.billing.entity.EnrollmentPaymentStatus;
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
    boolean existsByStudentIdAndStatusIn(Long studentId, Collection<ProgramEnrollmentStatus> statuses);

    List<ProgramEnrollment> findByStudentId(Long studentId);

    @Query("select e.id from ProgramEnrollment e where e.id in :ids and e.program.teachingMode = com.red_escuela.backend.academicstructure.entity.TeachingMode.ONLINE")
    List<Long> findIdsOfOnlineOnlyEnrollments(@Param("ids") Collection<Long> ids);

    List<ProgramEnrollment> findByStudentIdAndProgramIdAndStatusIn(
            Long studentId, Long programId, List<ProgramEnrollmentStatus> statuses);

    @Query("select e.id from ProgramEnrollment e where e.admissionApplication.id = :applicationId")
    java.util.Optional<Long> findIdByAdmissionApplicationId(@Param("applicationId") Long applicationId);

    List<ProgramEnrollment> findByStatus(ProgramEnrollmentStatus status);

    List<ProgramEnrollment> findByStatusInAndIdGreaterThanOrderByIdAsc(List<ProgramEnrollmentStatus> statuses, Long lastId, Pageable pageable);

    List<ProgramEnrollment> findByStudentIdAndProgramIdAndStatusAndPaymentStatus(
            Long studentId, Long programId, ProgramEnrollmentStatus status, EnrollmentPaymentStatus paymentStatus);

    @Query("select e.id from ProgramEnrollment e where e.paymentStatus = :standing and e.status in :statuses order by e.id")
    List<Long> findIdsByPaymentStatusAndStatusIn(@Param("standing") EnrollmentPaymentStatus standing,
                                                   @Param("statuses") Collection<ProgramEnrollmentStatus> statuses);

    @Query("select e.id from ProgramEnrollment e where e.program.id = :programId and e.status in :statuses order by e.id")
    List<Long> findIdsByProgramIdAndStatusIn(@Param("programId") Long programId, @Param("statuses") Collection<ProgramEnrollmentStatus> statuses);

    @Query("select distinct e.student.id from ProgramEnrollment e where e.status in :statuses")
    List<Long> findDistinctStudentIdsByStatusIn(@Param("statuses") Collection<ProgramEnrollmentStatus> statuses);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from ProgramEnrollment e where e.id = :id")
    java.util.Optional<ProgramEnrollment> findByIdForUpdate(@Param("id") Long id);

    interface BillingTemplateRef {
        Long getProgramId();

        Long getTemplateId();
    }

    @Query("""
            select e.program.id as programId, coalesce(current.id, cohortTemplate.id) as templateId
            from ProgramEnrollment e
            left join e.currentBillingTemplate current
            left join e.programCohort cohort
            left join cohort.template cohortTemplate
            where e.id = :id
            """)
    java.util.Optional<BillingTemplateRef> findBillingTemplateRef(@Param("id") Long id);
}
