package com.red_escuela.backend.enrollment.repository;

import com.red_escuela.backend.shared.domain.DocumentType;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ProgramEnrollmentRepository extends JpaRepository<ProgramEnrollment, Long> {
    interface PlanIdOfEnrollment {
        Long getEnrollmentId();

        Long getPlanId();
    }

    interface GraduationCandidateRow {
        Long getEnrollmentId();

        Long getStudentId();

        String getLastname();

        String getName();

        DocumentType getDocumentType();

        String getDocumentNumber();

        String getTitleName();
    }

    @Query("""
            select e.id as enrollmentId, e.student.id as studentId, p.lastname as lastname, p.name as name,
                   p.documentType as documentType, p.dni as documentNumber, e.program.name as titleName
            from ProgramEnrollment e join e.student s join s.person p
            where e.status = com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus.COMPLETED
              and e.completionDate is not null
              and (:pattern is null or lower(p.lastname) like :pattern or lower(p.name) like :pattern
                   or lower(concat(p.lastname, ' ', p.name)) like :pattern or lower(concat(p.name, ' ', p.lastname)) like :pattern
                   or p.dni like :pattern)
            order by p.lastname, p.name, e.id
            """)
    List<GraduationCandidateRow> findGraduationCandidates(@Param("pattern") String pattern);

    @Query("select e.id as enrollmentId, e.plan.id as planId from ProgramEnrollment e where e.id in :ids")
    List<PlanIdOfEnrollment> findPlanIdsByIdIn(@Param("ids") Collection<Long> ids);

    @Modifying(flushAutomatically = true)
    @Query("delete from ProgramEnrollment e where e.id in :ids")
    int deleteByIdIn(@Param("ids") Collection<Long> ids);

    boolean existsByProgramIdAndStatusIn(Long programId, Collection<ProgramEnrollmentStatus> statuses);

    boolean existsByPlanIdAndStatusIn(Long planId, Collection<ProgramEnrollmentStatus> statuses);

    boolean existsByStudentIdAndStatusIn(Long studentId, Collection<ProgramEnrollmentStatus> statuses);

    List<ProgramEnrollment> findByStudentId(Long studentId);

    @EntityGraph(attributePaths = {"student", "student.person", "program"})
    @Query("select e from ProgramEnrollment e where e.id in :ids")
    List<ProgramEnrollment> findAllWithStudentAndProgramByIdIn(@Param("ids") Collection<Long> ids);

    @Query("select e.id from ProgramEnrollment e where e.id in :ids and e.program.teachingMode = com.red_escuela.backend.academicstructure.entity.TeachingMode.ONLINE")
    List<Long> findIdsOfOnlineOnlyEnrollments(@Param("ids") Collection<Long> ids);

    List<ProgramEnrollment> findByStudentIdAndProgramIdAndStatusIn(
            Long studentId, Long programId, List<ProgramEnrollmentStatus> statuses);

    @Query("select e.id from ProgramEnrollment e where e.admissionApplication.id = :applicationId")
    java.util.Optional<Long> findIdByAdmissionApplicationId(@Param("applicationId") Long applicationId);

    List<ProgramEnrollment> findByStatus(ProgramEnrollmentStatus status);

    List<ProgramEnrollment> findByStatusInAndIdGreaterThanOrderByIdAsc(List<ProgramEnrollmentStatus> statuses, Long lastId, Pageable pageable);

    @Query("select e.id from ProgramEnrollment e where e.program.id = :programId and e.status in :statuses order by e.id")
    List<Long> findIdsByProgramIdAndStatusIn(@Param("programId") Long programId, @Param("statuses") Collection<ProgramEnrollmentStatus> statuses);

    @Query("select distinct e.student.id from ProgramEnrollment e where e.status in :statuses")
    List<Long> findDistinctStudentIdsByStatusIn(@Param("statuses") Collection<ProgramEnrollmentStatus> statuses);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from ProgramEnrollment e where e.id = :id")
    java.util.Optional<ProgramEnrollment> findByIdForUpdate(@Param("id") Long id);

    boolean existsByIdAndStatus(Long id, ProgramEnrollmentStatus status);

    interface BillingTemplateRef {
        Long getProgramId();

        Long getTemplateId();
    }

    @Query("""
            select e.program.id as programId, coalesce(current.id, cohort.templateId) as templateId
            from ProgramEnrollment e
            left join e.currentBillingTemplate current
            left join e.programCohort cohort
            where e.id = :id
            """)
    java.util.Optional<BillingTemplateRef> findBillingTemplateRef(@Param("id") Long id);

    @Query("select e.id as enrollmentId, e.program.id as targetId from ProgramEnrollment e where e.id in :ids")
    List<EnrollmentReminderTargetRow> findReminderTargetRows(@Param("ids") Collection<Long> ids);

    @Query("select e.id as enrollmentId, e.program.name as name from ProgramEnrollment e where e.id in :ids")
    List<EnrollmentNameRow> findNameRows(@Param("ids") Collection<Long> ids);

    @Query("select new com.red_escuela.backend.enrollment.repository.ProgramEnrollmentDateRow(e.id, e.student.id, e.program.id, e.enrollmentDate) from ProgramEnrollment e where e.student.id in :studentIds and e.program.id in :programIds order by e.id")
    List<ProgramEnrollmentDateRow> findDateRowsByStudentIdInAndProgramIdIn(@Param("studentIds") Collection<Long> studentIds, @Param("programIds") Collection<Long> programIds);

    java.util.Optional<ProgramEnrollment> findFirstByStudentIdAndProgramIdOrderByIdAsc(Long studentId, Long programId);

    @Query("select distinct e.student.person.id from ProgramEnrollment e where e.program.id = :programId and e.status = :status and e.student.person.status = com.red_escuela.backend.shared.domain.StatusType.ACTIVE")
    java.util.List<Long> findStudentPersonIdsByProgramAndStatus(Long programId, ProgramEnrollmentStatus status);

    @Query("select distinct e.student.person.id from ProgramEnrollment e where e.status = :status and e.student.person.status = com.red_escuela.backend.shared.domain.StatusType.ACTIVE")
    java.util.List<Long> findStudentPersonIdsByStatus(ProgramEnrollmentStatus status);
}
