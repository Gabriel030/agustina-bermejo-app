package com.red_escuela.backend.evaluation.repository;

import com.red_escuela.backend.evaluation.entity.ExamBoardRegistration;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistrationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExamBoardRegistrationRepository extends JpaRepository<ExamBoardRegistration, Long> {

    List<ExamBoardRegistration> findByExamBoardId(Long examBoardId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ExamBoardRegistration r where r.examBoard.id = :examBoardId and r.id in :ids order by r.id")
    List<ExamBoardRegistration> findByExamBoardIdAndIdInForUpdate(
            @Param("examBoardId") Long examBoardId, @Param("ids") java.util.Collection<Long> ids);

    @Query("""
            select r from ExamBoardRegistration r join fetch r.examBoard b join fetch b.subject
            where r.programSubjectSectionEnrollment.student.id = :studentId
              and b.examDate >= :start and b.examDate < :endExclusive
            order by b.examDate, b.id, r.id
            """)
    List<ExamBoardRegistration> findProgramRegistrationsOfStudentBetween(
            @Param("studentId") Long studentId, @Param("start") java.time.LocalDateTime start,
            @Param("endExclusive") java.time.LocalDateTime endExclusive);

    @Query("""
            select r from ExamBoardRegistration r join fetch r.examBoard b join fetch b.courseSubject
            where r.courseSubjectSectionEnrollment.student.id = :studentId
              and b.examDate >= :start and b.examDate < :endExclusive
            order by b.examDate, b.id, r.id
            """)
    List<ExamBoardRegistration> findCourseRegistrationsOfStudentBetween(
            @Param("studentId") Long studentId, @Param("start") java.time.LocalDateTime start,
            @Param("endExclusive") java.time.LocalDateTime endExclusive);

    List<ExamBoardRegistration> findByProgramSubjectSectionEnrollmentId(Long enrollmentId);

    List<ExamBoardRegistration> findByCourseSubjectSectionEnrollmentId(Long enrollmentId);

    Optional<ExamBoardRegistration> findByExamBoardIdAndProgramSubjectSectionEnrollmentId(
            Long examBoardId, Long enrollmentId);

    Optional<ExamBoardRegistration> findByExamBoardIdAndCourseSubjectSectionEnrollmentId(
            Long examBoardId, Long enrollmentId);

    long countByProgramSubjectSectionEnrollmentIdAndStatusNotIn(Long enrollmentId, java.util.Collection<ExamBoardRegistrationStatus> statuses);

    long countByCourseSubjectSectionEnrollmentIdAndStatusNotIn(Long enrollmentId, java.util.Collection<ExamBoardRegistrationStatus> statuses);

    List<ExamBoardRegistration> findByProgramSubjectSectionEnrollment_ProgramEnrollment_Student_Id(Long studentId);

    List<ExamBoardRegistration> findByProgramSubjectSectionEnrollment_ProgramEnrollment_IdAndProgramSubjectSectionEnrollment_SubjectSection_Subject_IdAndStatus(
            Long programEnrollmentId, Long subjectId, ExamBoardRegistrationStatus status);
}
