package com.red_escuela.backend.announcement.specialist;

import com.red_escuela.backend.announcement.entity.Announcement;
import com.red_escuela.backend.announcement.events.AnnouncementDeletedEvent;
import com.red_escuela.backend.announcement.exception.AnnouncementNotFoundException;
import com.red_escuela.backend.announcement.repository.AnnouncementRecipientRepository;
import com.red_escuela.backend.announcement.repository.AnnouncementRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeleteAnnouncementTransaction {

    private static final Logger log = LoggerFactory.getLogger(DeleteAnnouncementTransaction.class);

    private final AnnouncementRepository announcementRepository;
    private final AnnouncementRecipientRepository recipientRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final SectionMembershipChecker sectionMembership;

    public DeleteAnnouncementTransaction(AnnouncementRepository announcementRepository,
                                         AnnouncementRecipientRepository recipientRepository,
                                         ApplicationEventPublisher eventPublisher,
                                         SectionMembershipChecker sectionMembership) {
        this.announcementRepository = announcementRepository;
        this.recipientRepository = recipientRepository;
        this.eventPublisher = eventPublisher;
        this.sectionMembership = sectionMembership;
    }

    @Transactional
    public void delete(Long announcementId) {
        Announcement announcement = announcementRepository.findById(announcementId)
                .orElseThrow(() -> new AnnouncementNotFoundException(announcementId));

        sectionMembership.requireCurrentInstructorOf(announcement.getSectionType(), announcement.getSectionId());

        recipientRepository.deleteByAnnouncementId(announcementId);
        announcementRepository.delete(announcement);
        eventPublisher.publishEvent(AnnouncementDeletedEvent.deletedFor(announcementId));

        log.info("Aviso eliminado: id={}", announcementId);
    }
}
