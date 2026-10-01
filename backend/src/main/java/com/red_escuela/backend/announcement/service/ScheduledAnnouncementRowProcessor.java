package com.red_escuela.backend.announcement.service;

import com.red_escuela.backend.announcement.entity.Announcement;
import com.red_escuela.backend.announcement.exception.AnnouncementNotFoundException;
import com.red_escuela.backend.announcement.repository.AnnouncementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class ScheduledAnnouncementRowProcessor {

    private final AnnouncementRepository announcementRepository;
    private final AnnouncementDispatcher dispatcher;

    public ScheduledAnnouncementRowProcessor(AnnouncementRepository announcementRepository,
                                             AnnouncementDispatcher dispatcher) {
        this.announcementRepository = announcementRepository;
        this.dispatcher = dispatcher;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int process(Long announcementId, LocalDateTime when) {
        Announcement announcement = announcementRepository.findById(announcementId)
                .orElseThrow(() -> new AnnouncementNotFoundException(announcementId));
        return dispatcher.dispatchScheduled(announcement, when);
    }
}
