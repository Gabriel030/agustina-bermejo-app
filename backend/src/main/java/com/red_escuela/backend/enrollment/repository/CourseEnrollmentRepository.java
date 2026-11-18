package com.red_escuela.backend.enrollment.repository;

import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CourseEnrollmentRepository extends JpaRepository<CourseEnrollment, Long> {
    boolean existsByStudentIdAndStatusIn(Long studentId, Collection<EnrollmentStatus> statuses);

    List<CourseEnrollment> findByStudentId(Long studentId);

    @EntityGraph(attributePaths = {"student", "student.person", "course"})
    @Query("select e from CourseEnrollment e where e.id in :ids")
    List<CourseEnrollment> findAllWithStudentAndCourseByIdIn(@Param("ids") Collection<Long> ids);

    List<CourseEnrollment> findByStudentIdAndCourseIdAndStatusIn(
            Long studentId, Long courseId, List<EnrollmentStatus> statuses);

    List<CourseEnrollment> findByStatus(EnrollmentStatus status);

    List<CourseEnrollment> findByCourseIdAndStatusIn(Long courseId, List<EnrollmentStatus> statuses);

    long countByCourseIdAndStatusIn(Long courseId, List<EnrollmentStatus> statuses);

    Optional<CourseEnrollment> findFirstByCourseIdAndStatusOrderByEnrollmentDateAsc(Long courseId, EnrollmentStatus status);

    List<CourseEnrollment> findByCourseIdAndStatus(Long courseId, EnrollmentStatus status);

    List<CourseEnrollment> findByStatusAndIdGreaterThanOrderByIdAsc(EnrollmentStatus status, Long lastId, Pageable pageable);

    @Query("select distinct e.student.id from CourseEnrollment e where e.status in :statuses")
    List<Long> findDistinctStudentIdsByStatusIn(@Param("statuses") Collection<EnrollmentStatus> statuses);

    @Query("select c.term.endDate from CourseEnrollment e join e.course c where e.id = :id")
    java.util.Optional<java.time.LocalDateTime> findCourseTermEndDateByEnrollmentId(@Param("id") Long id);

    @Query("select e.id from CourseEnrollment e where e.id in :ids and e.course.teachingMode = com.red_escuela.backend.academicstructure.entity.TeachingMode.ONLINE")
    List<Long> findIdsOfOnlineOnlyEnrollments(@Param("ids") Collection<Long> ids);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from CourseEnrollment e where e.id = :id")
    java.util.Optional<CourseEnrollment> findByIdForUpdate(@Param("id") Long id);

    interface CourseTermDates {
        java.time.LocalDateTime getStartDate();

        java.time.LocalDateTime getEndDate();
    }

    @Query("select t.startDate as startDate, t.endDate as endDate from CourseEnrollment e join e.course c join c.term t where e.id = :id")
    Optional<CourseTermDates> findCourseTermDates(@Param("id") Long id);

    @Query("select e.id as enrollmentId, e.course.id as targetId from CourseEnrollment e where e.id in :ids")
    List<EnrollmentReminderTargetRow> findReminderTargetRows(@Param("ids") Collection<Long> ids);

    @Query("select e.id as enrollmentId, e.course.name as name from CourseEnrollment e where e.id in :ids")
    List<EnrollmentNameRow> findNameRows(@Param("ids") Collection<Long> ids);

    @Query("""
            select c.id as id, c.name as name, t.description as termDescription, t.year as termYear, c.capacity as capacity,
                   t.startDate as termStart, c.waitlistDeadlineOffsetDays as waitlistDeadlineOffsetDays,
                   count(case when e.status in :occupying then 1 end) as occupied,
                   count(case when e.status = :waiting then 1 end) as waiting
            from Course c join c.term t
            left join CourseEnrollment e on e.course = c
            where (:courseId is null or c.id = :courseId) and (:termId is null or t.id = :termId)
            group by c.id, c.name, t.description, t.year, c.capacity, t.startDate, c.waitlistDeadlineOffsetDays
            having (:courseId is not null or count(case when e.status = :waiting then 1 end) > 0)
            """)
    List<CourseWaitlistQueueRow> findWaitlistQueueRows(@Param("courseId") Long courseId, @Param("termId") Long termId,
                                                       @Param("occupying") Collection<EnrollmentStatus> occupying,
                                                       @Param("waiting") EnrollmentStatus waiting);

    @Query("""
            select e.id as enrollmentId, st.id as studentId, concat(p.name, ' ', p.lastname) as fullName, p.dni as documentNumber,
                   e.enrollmentDate as joinedAt, e.waitlistPrioritizedAt as prioritizedAt
            from CourseEnrollment e join e.student st join st.person p
            where e.course.id = :courseId and e.status = :waiting
            order by e.waitlistPrioritizedAt desc nulls last, e.enrollmentDate asc, e.id asc
            """)
    List<WaitlistEntryRow> findWaitlistEntryRows(@Param("courseId") Long courseId, @Param("waiting") EnrollmentStatus waiting);

    @Query("select e.id as enrollmentId, e.student.id as studentId from CourseEnrollment e where e.course.id = :courseId")
    List<WaitlistEnrollmentStudentRow> findWaitlistEnrollmentStudentRows(@Param("courseId") Long courseId);

    @Query("select e.id from CourseEnrollment e where e.status = :waiting and e.waitlistExpiryRemindedAt is null and e.course.id in :courseIds order by e.id")
    List<Long> findWaitlistedIdsToRemind(@Param("courseIds") java.util.Collection<Long> courseIds, @Param("waiting") EnrollmentStatus waiting);
}
