package com.red_escuela.backend.enrollment.repository;

import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CourseSubjectSectionEnrollmentRepository extends JpaRepository<CourseSubjectSectionEnrollment, Long> {
    boolean existsByCourseSubjectSectionIdAndStatusIn(Long sectionId, List<EnrollmentStatus> statuses);

    boolean existsByCourseSubjectSectionTermIdAndStatusIn(Long termId, List<EnrollmentStatus> statuses);

    long countByCourseSubjectSectionIdAndStatusIn(Long sectionId, List<EnrollmentStatus> statuses);


    List<CourseSubjectSectionEnrollment> findByCourseEnrollmentId(Long courseEnrollmentId);

    List<CourseSubjectSectionEnrollment> findByCourseEnrollmentIdAndStatusIn(Long courseEnrollmentId, List<EnrollmentStatus> statuses);

    List<CourseSubjectSectionEnrollment> findByStudentIdOrderByIdAsc(Long studentId);

    List<CourseSubjectSectionEnrollment> findByStudentIdAndStatusInOrderByIdAsc(Long studentId, List<EnrollmentStatus> statuses);

    List<CourseSubjectSectionEnrollment> findByStudentIdAndCourseEnrollmentIdOrderByIdAsc(Long studentId, Long courseEnrollmentId);

    List<CourseSubjectSectionEnrollment> findByCourseSubjectSectionIdAndStatusIn(
            Long courseSubjectSectionId, List<EnrollmentStatus> statuses);

    @Query("""
            select e from CourseSubjectSectionEnrollment e
            join fetch e.student s join fetch s.person join fetch e.courseSubjectSection
            where e.courseSubjectSection.id = :sectionId and e.status in :statuses
            """)
    List<CourseSubjectSectionEnrollment> findWithStudentBySectionIdAndStatusIn(
            @Param("sectionId") Long sectionId, @Param("statuses") List<EnrollmentStatus> statuses);

    Optional<CourseSubjectSectionEnrollment> findFirstByCourseSubjectSectionIdAndStatusOrderByEnrolledAtAsc(
            Long courseSubjectSectionId, EnrollmentStatus status);

    Optional<CourseSubjectSectionEnrollment> findByStudentIdAndCourseSubjectSectionId(Long studentId, Long courseSubjectSectionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from CourseSubjectSectionEnrollment e where e.id = :id")
    Optional<CourseSubjectSectionEnrollment> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select s.id as id, concat(cs.name, ', ', s.name) as name, t.description as termDescription, t.year as termYear,
                   s.capacity as capacity,
                   count(case when e.status in :occupying then 1 end) as occupied,
                   count(case when e.status = :waiting then 1 end) as waiting
            from CourseSubjectSection s join s.courseSubject cs join s.term t
            left join CourseSubjectSectionEnrollment e on e.courseSubjectSection = s
            where (:sectionId is null or s.id = :sectionId) and (:termId is null or t.id = :termId)
            group by s.id, cs.name, s.name, t.description, t.year, s.capacity
            having (:sectionId is not null or count(case when e.status = :waiting then 1 end) > 0)
            """)
    List<WaitlistQueueRow> findWaitlistQueueRows(@Param("sectionId") Long sectionId, @Param("termId") Long termId,
                                                 @Param("occupying") java.util.Collection<EnrollmentStatus> occupying,
                                                 @Param("waiting") EnrollmentStatus waiting);

    @Query("""
            select e.id as enrollmentId, st.id as studentId, concat(p.name, ' ', p.lastname) as fullName, p.dni as documentNumber,
                   e.enrolledAt as joinedAt, e.waitlistPrioritizedAt as prioritizedAt
            from CourseSubjectSectionEnrollment e join e.student st join st.person p
            where e.courseSubjectSection.id = :sectionId and e.status = :waiting
            order by e.waitlistPrioritizedAt asc nulls last, e.enrolledAt asc, e.id asc
            """)
    List<WaitlistEntryRow> findWaitlistEntryRows(@Param("sectionId") Long sectionId, @Param("waiting") EnrollmentStatus waiting);

    @Query("select e.id as enrollmentId, e.student.id as studentId from CourseSubjectSectionEnrollment e where e.courseSubjectSection.id = :sectionId")
    List<WaitlistEnrollmentStudentRow> findWaitlistEnrollmentStudentRows(@Param("sectionId") Long sectionId);
}
