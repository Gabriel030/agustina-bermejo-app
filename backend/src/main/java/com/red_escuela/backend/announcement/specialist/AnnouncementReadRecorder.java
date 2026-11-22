package com.red_escuela.backend.announcement.specialist;

import com.red_escuela.backend.announcement.events.AnnouncementReadEvent;
import com.red_escuela.backend.announcement.exception.AnnouncementNotFoundException;
import com.red_escuela.backend.announcement.repository.AnnouncementRecipientRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class AnnouncementReadRecorder {

    private final AnnouncementRecipientRepository recipientRepository;
    private final AnnouncementViewer announcementViewer;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    public AnnouncementReadRecorder(AnnouncementRecipientRepository recipientRepository,
                                    AnnouncementViewer announcementViewer, Clock clock,
                                    ApplicationEventPublisher eventPublisher) {
        this.recipientRepository = recipientRepository;
        this.announcementViewer = announcementViewer;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void recordReadByCurrentStudent(Long announcementId) {
        Long studentId = announcementViewer.currentStudentId()
                .orElseThrow(() -> new AnnouncementNotFoundException(announcementId));
        recordRead(announcementId, studentId);
        eventPublisher.publishEvent(AnnouncementReadEvent.readFor(announcementId, announcementViewer.currentPersonId()));
    }

    @Transactional
    public void recordRead(Long announcementId, Long studentId) {
        int updated = recipientRepository.markReadIfUnread(announcementId, studentId, LocalDateTime.now(clock));
        if (updated == 0 && !recipientRepository.existsByAnnouncementIdAndStudentId(announcementId, studentId)) {
            throw new AnnouncementNotFoundException(announcementId);
        }
    }
}
