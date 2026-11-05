package com.red_escuela.backend.announcement.repository;

import com.red_escuela.backend.announcement.entity.AnnouncementRecipient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface AnnouncementRecipientRepository extends JpaRepository<AnnouncementRecipient, Long> {

    interface ReadCounts {

        Long getAnnouncementId();

        long getTotal();

        long getReadCount();
    }

    List<AnnouncementRecipient> findByAnnouncementId(Long announcementId);

    boolean existsByAnnouncementIdAndStudentId(Long announcementId, Long studentId);

    void deleteByAnnouncementId(Long announcementId);

    @Query("""
            select r.announcement.id as announcementId, count(r) as total, count(r.readAt) as readCount
            from AnnouncementRecipient r
            where r.announcement.id in :ids
            group by r.announcement.id
            """)
    List<ReadCounts> countReadByAnnouncementIds(@Param("ids") Collection<Long> ids);

    @Modifying
    @Query("""
            update AnnouncementRecipient r set r.readAt = :readAt
            where r.announcement.id = :announcementId
              and r.student.id = :studentId
              and r.readAt is null
              and exists (select 1 from Announcement a where a.id = :announcementId
                          and a.status = com.red_escuela.backend.announcement.entity.AnnouncementStatus.SENT)
            """)
    int markReadIfUnread(@Param("announcementId") Long announcementId,
                         @Param("studentId") Long studentId,
                         @Param("readAt") LocalDateTime readAt);
}
