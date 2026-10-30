package com.red_escuela.backend.enrollment.repository;

import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import org.springframework.data.domain.Pageable;
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
}
