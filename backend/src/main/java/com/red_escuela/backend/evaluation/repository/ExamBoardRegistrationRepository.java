package com.red_escuela.backend.evaluation.repository;

import com.red_escuela.backend.evaluation.entity.ExamBoardRegistration;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistrationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExamBoardRegistrationRepository extends JpaRepository<ExamBoardRegistration, Long> {

    List<ExamBoardRegistration> findByExamBoardId(Long examBoardId);

    List<ExamBoardRegistration> findByProgramSubjectSectionEnrollmentId(Long enrollmentId);

    List<ExamBoardRegistration> findByCourseSubjectSectionEnrollmentId(Long enrollmentId);

    Optional<ExamBoardRegistration> findByExamBoardIdAndProgramSubjectSectionEnrollmentId(
            Long examBoardId, Long enrollmentId);

    Optional<ExamBoardRegistration> findByExamBoardIdAndCourseSubjectSectionEnrollmentId(
            Long examBoardId, Long enrollmentId);

    long countByProgramSubjectSectionEnrollmentIdAndStatusNot(Long enrollmentId, ExamBoardRegistrationStatus status);

    long countByCourseSubjectSectionEnrollmentIdAndStatusNot(Long enrollmentId, ExamBoardRegistrationStatus status);

    List<ExamBoardRegistration> findByProgramSubjectSectionEnrollment_ProgramEnrollment_Student_Id(Long studentId);

    List<ExamBoardRegistration> findByProgramSubjectSectionEnrollment_ProgramEnrollment_IdAndProgramSubjectSectionEnrollment_SubjectSection_Subject_IdAndStatus(
            Long programEnrollmentId, Long subjectId, ExamBoardRegistrationStatus status);
}
