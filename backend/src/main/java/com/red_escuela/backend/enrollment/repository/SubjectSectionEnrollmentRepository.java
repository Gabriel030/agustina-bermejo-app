package com.red_escuela.backend.enrollment.repository;

import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubjectSectionEnrollmentRepository extends JpaRepository<SubjectSectionEnrollment, Long> {
    List<SubjectSectionEnrollment> findByProgramEnrollmentId(Long programEnrollmentId);

    List<SubjectSectionEnrollment> findByStudentId(Long studentId);

    List<SubjectSectionEnrollment> findBySubjectSectionIdAndStatusIn(Long subjectSectionId, List<EnrollmentStatus> statuses);

    Optional<SubjectSectionEnrollment> findFirstBySubjectSectionIdAndStatusOrderByEnrolledAtAsc(
            Long subjectSectionId, EnrollmentStatus status);

    List<SubjectSectionEnrollment> findByProgramEnrollmentIdAndSubjectSection_Subject_IdAndStatusIn(
            Long programEnrollmentId, Long subjectId, List<EnrollmentStatus> statuses);

    Optional<SubjectSectionEnrollment> findByStudentIdAndSubjectSectionId(Long studentId, Long subjectSectionId);

    boolean existsByProgramEnrollmentIdAndStatusIn(Long programEnrollmentId, List<EnrollmentStatus> statuses);
}
