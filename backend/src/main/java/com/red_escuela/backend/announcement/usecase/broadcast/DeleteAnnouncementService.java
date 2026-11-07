package com.red_escuela.backend.announcement.usecase.broadcast;

import com.red_escuela.backend.announcement.entity.Announcement;
import com.red_escuela.backend.announcement.events.AnnouncementDeletedEvent;
import com.red_escuela.backend.announcement.exception.AnnouncementNotFoundException;
import com.red_escuela.backend.announcement.repository.AnnouncementRecipientRepository;
import com.red_escuela.backend.announcement.repository.AnnouncementRepository;
import com.red_escuela.backend.announcement.specialist.SectionMembershipChecker;
import com.red_escuela.backend.shared.UseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class DeleteAnnouncementService implements DeleteAnnouncementUseCase {

    private static final Logger log = LoggerFactory.getLogger(DeleteAnnouncementService.class);

    private final AnnouncementRepository announcementRepository;
    private final AnnouncementRecipientRepository recipientRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final SectionMembershipChecker sectionMembership;

    public DeleteAnnouncementService(AnnouncementRepository announcementRepository,
                                     AnnouncementRecipientRepository recipientRepository,
                                     ApplicationEventPublisher eventPublisher,
                                     SectionMembershipChecker sectionMembership) {
        this.announcementRepository = announcementRepository;
        this.recipientRepository = recipientRepository;
        this.eventPublisher = eventPublisher;
        this.sectionMembership = sectionMembership;
    }

    @Override
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
