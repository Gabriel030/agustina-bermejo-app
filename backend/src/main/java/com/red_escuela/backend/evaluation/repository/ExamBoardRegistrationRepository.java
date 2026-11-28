package com.red_escuela.backend.evaluation.repository;

import com.red_escuela.backend.evaluation.entity.ExamBoardRegistration;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistrationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExamBoardRegistrationRepository extends JpaRepository<ExamBoardRegistration, Long> {

    List<ExamBoardRegistration> findByExamBoardId(Long examBoardId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ExamBoardRegistration r where r.examBoard.id = :examBoardId and r.id in :ids order by r.id")
    List<ExamBoardRegistration> findByExamBoardIdAndIdInForUpdate(
            @Param("examBoardId") Long examBoardId, @Param("ids") Collection<Long> ids);

    @Query("""
            select r from ExamBoardRegistration r join fetch r.examBoard b join fetch b.subject
            where r.programSubjectSectionEnrollment.student.id = :studentId
              and b.examDate >= :start and b.examDate < :endExclusive
            order by b.examDate, b.id, r.id
            """)
    List<ExamBoardRegistration> findProgramRegistrationsOfStudentBetween(
            @Param("studentId") Long studentId, @Param("start") LocalDateTime start,
            @Param("endExclusive") LocalDateTime endExclusive);

    @Query("""
            select r from ExamBoardRegistration r join fetch r.examBoard b join fetch b.courseSubject
            where r.courseSubjectSectionEnrollment.student.id = :studentId
              and b.examDate >= :start and b.examDate < :endExclusive
            order by b.examDate, b.id, r.id
            """)
    List<ExamBoardRegistration> findCourseRegistrationsOfStudentBetween(
            @Param("studentId") Long studentId, @Param("start") LocalDateTime start,
            @Param("endExclusive") LocalDateTime endExclusive);

    List<ExamBoardRegistration> findByProgramSubjectSectionEnrollmentId(Long enrollmentId);

    List<ExamBoardRegistration> findByCourseSubjectSectionEnrollmentId(Long enrollmentId);

    Optional<ExamBoardRegistration> findByExamBoardIdAndProgramSubjectSectionEnrollmentId(
            Long examBoardId, Long enrollmentId);

    Optional<ExamBoardRegistration> findByExamBoardIdAndCourseSubjectSectionEnrollmentId(
            Long examBoardId, Long enrollmentId);

    long countByProgramSubjectSectionEnrollmentIdAndStatusNotIn(Long enrollmentId, Collection<ExamBoardRegistrationStatus> statuses);

    long countByCourseSubjectSectionEnrollmentIdAndStatusNotIn(Long enrollmentId, Collection<ExamBoardRegistrationStatus> statuses);

    List<ExamBoardRegistration> findByProgramSubjectSectionEnrollment_ProgramEnrollment_Student_Id(Long studentId);

    List<ExamBoardRegistration> findByProgramSubjectSectionEnrollment_ProgramEnrollment_IdAndProgramSubjectSectionEnrollment_SubjectSection_Subject_IdAndStatus(
            Long programEnrollmentId, Long subjectId, ExamBoardRegistrationStatus status);

    @Query("""
            select r from ExamBoardRegistration r join fetch r.examBoard b join fetch b.subject
            join fetch r.programSubjectSectionEnrollment e join fetch e.programEnrollment pe join fetch pe.program
            where e.student.id = :studentId and r.status = com.red_escuela.backend.evaluation.entity.ExamBoardRegistrationStatus.REGISTERED
              and b.status = com.red_escuela.backend.evaluation.entity.ExamBoardStatus.SCHEDULED and b.examDate >= :notBefore
            """)
    List<ExamBoardRegistration> findActiveProgramRegistrationsOfStudentFrom(@Param("studentId") Long studentId,
                                                                            @Param("notBefore") LocalDateTime notBefore);

    @Query("""
            select r from ExamBoardRegistration r join fetch r.examBoard b join fetch b.courseSubject cs join fetch cs.course
            join fetch r.courseSubjectSectionEnrollment e
            where e.student.id = :studentId and r.status = com.red_escuela.backend.evaluation.entity.ExamBoardRegistrationStatus.REGISTERED
              and b.status = com.red_escuela.backend.evaluation.entity.ExamBoardStatus.SCHEDULED and b.examDate >= :notBefore
            """)
    List<ExamBoardRegistration> findActiveCourseRegistrationsOfStudentFrom(@Param("studentId") Long studentId,
                                                                           @Param("notBefore") LocalDateTime notBefore);

    @Query("""
            select r from ExamBoardRegistration r join fetch r.examBoard b join fetch b.subject
            join fetch r.programSubjectSectionEnrollment e join fetch e.programEnrollment pe join fetch pe.program
            where e.student.id = :studentId and r.status = com.red_escuela.backend.evaluation.entity.ExamBoardRegistrationStatus.GRADED
              and b.status = com.red_escuela.backend.evaluation.entity.ExamBoardStatus.CLOSED
            """)
    List<ExamBoardRegistration> findAttendedProgramRegistrationsOfStudent(@Param("studentId") Long studentId);

    @Query("""
            select r from ExamBoardRegistration r join fetch r.examBoard b join fetch b.courseSubject cs join fetch cs.course
            join fetch r.courseSubjectSectionEnrollment e
            where e.student.id = :studentId and r.status = com.red_escuela.backend.evaluation.entity.ExamBoardRegistrationStatus.GRADED
              and b.status = com.red_escuela.backend.evaluation.entity.ExamBoardStatus.CLOSED
            """)
    List<ExamBoardRegistration> findAttendedCourseRegistrationsOfStudent(@Param("studentId") Long studentId);

    @Query("""
            select count(r) > 0 from ExamBoardRegistration r
            join r.examBoard b
            left join r.programSubjectSectionEnrollment pe
            left join r.courseSubjectSectionEnrollment ce
            where r.status = com.red_escuela.backend.evaluation.entity.ExamBoardRegistrationStatus.REGISTERED
              and b.id <> :examBoardId and b.examDate = :examDate
              and b.status = com.red_escuela.backend.evaluation.entity.ExamBoardStatus.SCHEDULED
              and (pe.student.id = :studentId or ce.student.id = :studentId)
            """)
    boolean existsRegisteredOfStudentAt(@Param("studentId") Long studentId, @Param("examBoardId") Long examBoardId,
                                        @Param("examDate") LocalDateTime examDate);
}
