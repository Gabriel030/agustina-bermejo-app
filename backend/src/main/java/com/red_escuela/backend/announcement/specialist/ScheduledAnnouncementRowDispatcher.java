package com.red_escuela.backend.announcement.specialist;

import com.red_escuela.backend.announcement.entity.Announcement;
import com.red_escuela.backend.announcement.exception.AnnouncementNotFoundException;
import com.red_escuela.backend.announcement.repository.AnnouncementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class ScheduledAnnouncementRowDispatcher {

    private final AnnouncementRepository announcementRepository;
    private final AnnouncementDispatcher dispatcher;

    public ScheduledAnnouncementRowDispatcher(AnnouncementRepository announcementRepository,
                                             AnnouncementDispatcher dispatcher) {
        this.announcementRepository = announcementRepository;
        this.dispatcher = dispatcher;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int dispatchRow(Long announcementId, LocalDateTime when) {
        Announcement announcement = announcementRepository.findById(announcementId)
                .orElseThrow(() -> new AnnouncementNotFoundException(announcementId));
        return dispatcher.dispatchScheduled(announcement, when);
    }
}
