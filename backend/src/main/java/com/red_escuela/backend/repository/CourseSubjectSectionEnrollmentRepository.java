package com.red_escuela.backend.repository;

import com.red_escuela.backend.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.entity.EnrollmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CourseSubjectSectionEnrollmentRepository extends JpaRepository<CourseSubjectSectionEnrollment, Long> {

    List<CourseSubjectSectionEnrollment> findByCourseEnrollmentId(Long courseEnrollmentId);

    List<CourseSubjectSectionEnrollment> findByCourseSubjectSectionIdAndStatusIn(
            Long courseSubjectSectionId, List<EnrollmentStatus> statuses);

    Optional<CourseSubjectSectionEnrollment> findFirstByCourseSubjectSectionIdAndStatusOrderByEnrolledAtAsc(
            Long courseSubjectSectionId, EnrollmentStatus status);

    Optional<CourseSubjectSectionEnrollment> findByStudentIdAndCourseSubjectSectionId(Long studentId, Long courseSubjectSectionId);
}
