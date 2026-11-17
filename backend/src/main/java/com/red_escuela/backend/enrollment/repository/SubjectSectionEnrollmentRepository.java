package com.red_escuela.backend.enrollment.repository;

import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubjectSectionEnrollmentRepository extends JpaRepository<SubjectSectionEnrollment, Long> {
    long countBySubjectSectionIdAndStatusIn(Long sectionId, List<EnrollmentStatus> statuses);

    List<SubjectSectionEnrollment> findByProgramEnrollmentId(Long programEnrollmentId);

    List<SubjectSectionEnrollment> findByProgramEnrollmentIdAndStatusIn(Long programEnrollmentId, List<EnrollmentStatus> statuses);

    List<SubjectSectionEnrollment> findByStudentId(Long studentId);

    @Query("""
            select distinct e from SubjectSectionEnrollment e
            join fetch e.programEnrollment pe join fetch pe.program
            join fetch e.subjectSection ss join fetch ss.subject
            left join fetch ss.instructors i left join fetch i.instructor ins left join fetch ins.person
            where pe.id = :programEnrollmentId
            """)
    List<SubjectSectionEnrollment> findDetailedByProgramEnrollmentId(@Param("programEnrollmentId") Long programEnrollmentId);

    @Query("""
            select distinct e from SubjectSectionEnrollment e
            join fetch e.programEnrollment pe join fetch pe.program
            join fetch e.subjectSection ss join fetch ss.subject
            left join fetch ss.instructors i left join fetch i.instructor ins left join fetch ins.person
            where e.student.id = :studentId
            """)
    List<SubjectSectionEnrollment> findDetailedByStudentId(@Param("studentId") Long studentId);

    @Query("""
            select e from SubjectSectionEnrollment e
            join fetch e.subjectSection ss join fetch ss.subject
            where e.id in :ids
            """)
    List<SubjectSectionEnrollment> findWithSectionByIdIn(@Param("ids") java.util.Collection<Long> ids);

    @Query("""
            select e from SubjectSectionEnrollment e
            join fetch e.subjectSection ss join fetch ss.subject
            where e.programEnrollment.id = :programEnrollmentId
            """)
    List<SubjectSectionEnrollment> findWithSectionByProgramEnrollmentId(@Param("programEnrollmentId") Long programEnrollmentId);

    List<SubjectSectionEnrollment> findBySubjectSectionIdAndStatusIn(Long subjectSectionId, List<EnrollmentStatus> statuses);

    @Query("""
            select e from SubjectSectionEnrollment e
            join fetch e.student s join fetch s.person join fetch e.subjectSection
            where e.subjectSection.id = :sectionId and e.status in :statuses
            """)
    List<SubjectSectionEnrollment> findWithStudentBySectionIdAndStatusIn(
            @Param("sectionId") Long sectionId, @Param("statuses") List<EnrollmentStatus> statuses);

    Optional<SubjectSectionEnrollment> findFirstBySubjectSectionIdAndStatusOrderByEnrolledAtAsc(
            Long subjectSectionId, EnrollmentStatus status);

    List<SubjectSectionEnrollment> findBySubjectSectionIdAndStatusOrderByEnrolledAtAsc(
            Long subjectSectionId, EnrollmentStatus status);

    interface PrerequisiteStatusRow {
        Long getProgramEnrollmentId();

        Long getSubjectId();

        EnrollmentStatus getStatus();
    }

    @Query("""
            select e.programEnrollment.id as programEnrollmentId, e.subjectSection.subject.id as subjectId, e.status as status
            from SubjectSectionEnrollment e
            where e.programEnrollment.id in :programEnrollmentIds and e.subjectSection.subject.id in :subjectIds
            and e.status in :statuses
            """)
    List<PrerequisiteStatusRow> findPrerequisiteStatusRows(
            @Param("programEnrollmentIds") java.util.Collection<Long> programEnrollmentIds,
            @Param("subjectIds") java.util.Collection<Long> subjectIds,
            @Param("statuses") java.util.Collection<EnrollmentStatus> statuses);

    List<SubjectSectionEnrollment> findByProgramEnrollmentIdAndSubjectSection_Subject_IdAndStatusIn(
            Long programEnrollmentId, Long subjectId, List<EnrollmentStatus> statuses);

    Optional<SubjectSectionEnrollment> findByStudentIdAndSubjectSectionId(Long studentId, Long subjectSectionId);

    boolean existsBySubjectSectionIdAndStatusIn(Long sectionId, List<EnrollmentStatus> statuses);

    boolean existsBySubjectSectionTermIdAndStatusIn(Long termId, List<EnrollmentStatus> statuses);

    boolean existsByProgramEnrollmentIdAndStatusIn(Long programEnrollmentId, List<EnrollmentStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from SubjectSectionEnrollment e where e.id = :id")
    Optional<SubjectSectionEnrollment> findByIdForUpdate(@Param("id") Long id);

    @Query("select e.programEnrollment.id from SubjectSectionEnrollment e where e.id = :id")
    Optional<Long> findProgramEnrollmentIdById(@Param("id") Long id);

    @Query("select e.subjectSection.id from SubjectSectionEnrollment e where e.id = :id")
    Optional<Long> findSubjectSectionIdById(@Param("id") Long id);

    @Query("""
            select s.id as id, concat(sub.name, ', ', s.name) as name, t.description as termDescription, t.year as termYear,
                   s.capacity as capacity,
                   count(case when e.status in :occupying then 1 end) as occupied,
                   count(case when e.status = :waiting then 1 end) as waiting
            from SubjectSection s join s.subject sub join s.term t
            left join SubjectSectionEnrollment e on e.subjectSection = s
            where (:sectionId is null or s.id = :sectionId) and (:termId is null or t.id = :termId)
            group by s.id, sub.name, s.name, t.description, t.year, s.capacity
            having (:sectionId is not null or count(case when e.status = :waiting then 1 end) > 0)
            """)
    List<WaitlistQueueRow> findWaitlistQueueRows(@Param("sectionId") Long sectionId, @Param("termId") Long termId,
                                                 @Param("occupying") java.util.Collection<EnrollmentStatus> occupying,
                                                 @Param("waiting") EnrollmentStatus waiting);

    @Query("""
            select e.id as enrollmentId, st.id as studentId, concat(p.name, ' ', p.lastname) as fullName, p.dni as documentNumber,
                   e.enrolledAt as joinedAt, e.waitlistPrioritizedAt as prioritizedAt, e.programEnrollment.id as programEnrollmentId, s.subject.id as subjectId,
                   s.id as sectionId, s.term.id as termId
            from SubjectSectionEnrollment e join e.student st join st.person p join e.subjectSection s
            where s.id = :sectionId and e.status = :waiting
            order by e.waitlistPrioritizedAt asc nulls last, e.enrolledAt asc, e.id asc
            """)
    List<ProgramWaitlistEntryRow> findWaitlistEntryRows(@Param("sectionId") Long sectionId, @Param("waiting") EnrollmentStatus waiting);

    @Query("select e.id as enrollmentId, e.student.id as studentId from SubjectSectionEnrollment e where e.subjectSection.id = :sectionId")
    List<WaitlistEnrollmentStudentRow> findWaitlistEnrollmentStudentRows(@Param("sectionId") Long sectionId);

    @Query("select new com.red_escuela.backend.enrollment.repository.SectionEnrollmentPairRow(e.student.id, e.subjectSection.id) from SubjectSectionEnrollment e where e.student.id in :studentIds and e.subjectSection.id in :sectionIds")
    List<SectionEnrollmentPairRow> findPairRowsByStudentIdInAndSubjectSectionIdIn(@Param("studentIds") java.util.Collection<Long> studentIds, @Param("sectionIds") java.util.Collection<Long> sectionIds);
}
