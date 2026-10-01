package com.red_escuela.backend.announcement.repository;

import com.red_escuela.backend.announcement.entity.AnnouncementRecipient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnnouncementRecipientRepository extends JpaRepository<AnnouncementRecipient, Long> {

    List<AnnouncementRecipient> findByAnnouncementId(Long announcementId);

    void deleteByAnnouncementId(Long announcementId);
}
