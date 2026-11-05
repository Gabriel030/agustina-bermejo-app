package com.red_escuela.backend.announcement.service;

import com.red_escuela.backend.announcement.exception.AnnouncementNotFoundException;
import com.red_escuela.backend.announcement.repository.AnnouncementRecipientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class AnnouncementReadRecorder {

    private final AnnouncementRecipientRepository recipientRepository;
    private final Clock clock;

    public AnnouncementReadRecorder(AnnouncementRecipientRepository recipientRepository, Clock clock) {
        this.recipientRepository = recipientRepository;
        this.clock = clock;
    }

    @Transactional
    public void recordRead(Long announcementId, Long studentId) {
        int updated = recipientRepository.markReadIfUnread(announcementId, studentId, LocalDateTime.now(clock));
        if (updated == 0 && !recipientRepository.existsByAnnouncementIdAndStudentId(announcementId, studentId)) {
            throw new AnnouncementNotFoundException(announcementId);
        }
    }
}
