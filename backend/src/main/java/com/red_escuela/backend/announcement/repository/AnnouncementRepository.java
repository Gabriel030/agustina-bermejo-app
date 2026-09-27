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
            select r.announcement from AnnouncementRecipient r
            where r.student.id = :studentId
              and (r.announcement.expiresAt is null or r.announcement.expiresAt >= :today)
            order by r.announcement.createdAt desc
            """)
    List<Announcement> findFeedForStudent(@Param("studentId") Long studentId,
                                          @Param("today") LocalDate today);
}
