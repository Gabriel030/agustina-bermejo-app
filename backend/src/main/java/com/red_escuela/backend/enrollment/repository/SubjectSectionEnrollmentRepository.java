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

    List<SubjectSectionEnrollment> findByProgramEnrollmentIdAndSubjectSection_Subject_IdAndStatusIn(
            Long programEnrollmentId, Long subjectId, List<EnrollmentStatus> statuses);

    Optional<SubjectSectionEnrollment> findByStudentIdAndSubjectSectionId(Long studentId, Long subjectSectionId);

    boolean existsByProgramEnrollmentIdAndStatusIn(Long programEnrollmentId, List<EnrollmentStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from SubjectSectionEnrollment e where e.id = :id")
    Optional<SubjectSectionEnrollment> findByIdForUpdate(@Param("id") Long id);

    @Query("select e.programEnrollment.id from SubjectSectionEnrollment e where e.id = :id")
    Optional<Long> findProgramEnrollmentIdById(@Param("id") Long id);

    @Query("select e.subjectSection.id from SubjectSectionEnrollment e where e.id = :id")
    Optional<Long> findSubjectSectionIdById(@Param("id") Long id);
}
