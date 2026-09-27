package com.red_escuela.backend.announcement.repository;

import com.red_escuela.backend.announcement.entity.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    List<Announcement> findBySubjectSectionIdOrderByCreatedAtDesc(Long sectionId);

    List<Announcement> findByCourseSubjectSectionIdOrderByCreatedAtDesc(Long sectionId);

    @Query("""
            select a from Announcement a
            where (a.expiresAt is null or a.expiresAt >= :today)
              and (
                a.subjectSection.id in (
                    select e.subjectSection.id from SubjectSectionEnrollment e
                    where e.student.id = :studentId and e.status in :statuses
                )
                or a.courseSubjectSection.id in (
                    select c.courseSubjectSection.id from CourseSubjectSectionEnrollment c
                    where c.student.id = :studentId and c.status in :statuses
                )
              )
            order by a.createdAt desc
            """)
    List<Announcement> findFeedForStudent(@Param("studentId") Long studentId,
                                          @Param("statuses") List<com.red_escuela.backend.enrollment.entity.EnrollmentStatus> statuses,
                                          @Param("today") LocalDate today);
}
