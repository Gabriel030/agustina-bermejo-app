package com.red_escuela.backend.evaluation.repository;

import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.evaluation.entity.ExamBoard;
import com.red_escuela.backend.evaluation.entity.ExamBoardStatus;
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
public interface ExamBoardRepository extends JpaRepository<ExamBoard, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from ExamBoard b where b.id = :id")
    Optional<ExamBoard> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select b from ExamBoard b join fetch b.subject s
            where b.status = com.red_escuela.backend.evaluation.entity.ExamBoardStatus.SCHEDULED
              and b.examDate >= :start and b.examDate < :endExclusive
              and (b.registrationStart is null or b.registrationStart <= :now)
              and (b.registrationEnd is null or b.registrationEnd >= :now)
              and s.id in (select e.subjectSection.subject.id from SubjectSectionEnrollment e
                           where e.student.id = :studentId and e.status in :statuses)
            order by b.examDate, b.id
            """)
    List<ExamBoard> findOpenProgramBoardsForStudent(
            @Param("studentId") Long studentId, @Param("statuses") Collection<EnrollmentStatus> statuses,
            @Param("start") LocalDateTime start, @Param("endExclusive") LocalDateTime endExclusive,
            @Param("now") LocalDateTime now);

    @Query("""
            select b from ExamBoard b join fetch b.courseSubject cs
            where b.status = com.red_escuela.backend.evaluation.entity.ExamBoardStatus.SCHEDULED
              and b.examDate >= :start and b.examDate < :endExclusive
              and (b.registrationStart is null or b.registrationStart <= :now)
              and (b.registrationEnd is null or b.registrationEnd >= :now)
              and cs.id in (select e.courseSubjectSection.courseSubject.id from CourseSubjectSectionEnrollment e
                            where e.student.id = :studentId and e.status in :statuses)
            order by b.examDate, b.id
            """)
    List<ExamBoard> findOpenCourseBoardsForStudent(
            @Param("studentId") Long studentId, @Param("statuses") Collection<EnrollmentStatus> statuses,
            @Param("start") LocalDateTime start, @Param("endExclusive") LocalDateTime endExclusive,
            @Param("now") LocalDateTime now);

    List<ExamBoard> findBySubjectIdAndTermId(Long subjectId, Long termId);

    List<ExamBoard> findByCourseSubjectIdAndTermId(Long courseSubjectId, Long termId);

    List<ExamBoard> findBySubjectIdAndStatus(Long subjectId, ExamBoardStatus status);

    List<ExamBoard> findAllByOrderByExamDateDesc();

    List<ExamBoard> findByStatusOrderByExamDateDesc(ExamBoardStatus status);
}
