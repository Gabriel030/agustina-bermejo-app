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
}
