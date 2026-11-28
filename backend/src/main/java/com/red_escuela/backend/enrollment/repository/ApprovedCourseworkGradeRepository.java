package com.red_escuela.backend.enrollment.repository;

import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.shared.domain.Grade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ApprovedCourseworkGradeRepository extends JpaRepository<SubjectSectionEnrollment, Long> {

    interface ApprovedCourseworkGradeRow {
        Long getSubjectId();

        Grade getFinalScore();

        LocalDate getFinalScoreDate();
    }

    @Query("""
            select e.subjectSection.subject.id as subjectId, e.finalScore as finalScore, e.finalScoreDate as finalScoreDate
            from SubjectSectionEnrollment e
            where e.programEnrollment.id = :programEnrollmentId and e.status = :status
            order by e.finalScoreDate desc nulls last, e.id desc
            """)
    List<ApprovedCourseworkGradeRow> findByProgramEnrollmentAndStatusLatestFirst(
            @Param("programEnrollmentId") Long programEnrollmentId, @Param("status") EnrollmentStatus status);
}
