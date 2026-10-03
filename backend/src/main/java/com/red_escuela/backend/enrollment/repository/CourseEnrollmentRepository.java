package com.red_escuela.backend.enrollment.repository;

import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.billing.entity.EnrollmentBillingStanding;
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
    List<CourseEnrollment> findByStudentId(Long studentId);

    List<CourseEnrollment> findByStudentIdAndCourseIdAndStatusIn(
            Long studentId, Long courseId, List<EnrollmentStatus> statuses);

    List<CourseEnrollment> findByStatus(EnrollmentStatus status);

    List<CourseEnrollment> findByCourseIdAndStatusIn(Long courseId, List<EnrollmentStatus> statuses);

    long countByCourseIdAndStatusIn(Long courseId, List<EnrollmentStatus> statuses);

    Optional<CourseEnrollment> findFirstByCourseIdAndStatusOrderByEnrollmentDateAsc(Long courseId, EnrollmentStatus status);

    List<CourseEnrollment> findByCourseIdAndStatus(Long courseId, EnrollmentStatus status);

    List<CourseEnrollment> findByStatusAndIdGreaterThanOrderByIdAsc(EnrollmentStatus status, Long lastId, Pageable pageable);

    List<CourseEnrollment> findByStudentIdAndCourseIdAndStatusAndBillingStanding(
            Long studentId, Long courseId, EnrollmentStatus status, EnrollmentBillingStanding billingStanding);

    @Query("select distinct e.student.id from CourseEnrollment e where e.status in :statuses")
    List<Long> findDistinctStudentIdsByStatusIn(@Param("statuses") Collection<EnrollmentStatus> statuses);
}
