package com.red_escuela.backend.announcement.usecase.announcement;

import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.announcement.entity.Announcement;
import com.red_escuela.backend.announcement.events.AnnouncementDeletedEvent;
import com.red_escuela.backend.announcement.exception.AnnouncementNotFoundException;
import com.red_escuela.backend.announcement.repository.AnnouncementRecipientRepository;
import com.red_escuela.backend.announcement.repository.AnnouncementRepository;
import com.red_escuela.backend.announcement.service.SectionMembership;
import com.red_escuela.backend.academicstructure.service.CurrentInstructorService;
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
    private final SectionMembership sectionMembership;
    private final CurrentInstructorService currentInstructorService;

    public DeleteAnnouncementService(AnnouncementRepository announcementRepository,
                                     AnnouncementRecipientRepository recipientRepository,
                                     ApplicationEventPublisher eventPublisher,
                                     SectionMembership sectionMembership,
                                     CurrentInstructorService currentInstructorService) {
        this.announcementRepository = announcementRepository;
        this.recipientRepository = recipientRepository;
        this.eventPublisher = eventPublisher;
        this.sectionMembership = sectionMembership;
        this.currentInstructorService = currentInstructorService;
    }

    @Override
    @Transactional
    public void delete(Long announcementId) {
        Announcement announcement = announcementRepository.findById(announcementId)
                .orElseThrow(() -> new AnnouncementNotFoundException(announcementId));

        Instructor instructor = currentInstructorService.resolveCurrentInstructor();
        sectionMembership.requireInstructorOf(announcement.getSectionType(), announcement.getSectionId(), instructor.getId());

        recipientRepository.deleteByAnnouncementId(announcementId);
        announcementRepository.delete(announcement);
        eventPublisher.publishEvent(AnnouncementDeletedEvent.deletedFor(announcementId));

        log.info("Aviso eliminado: id={}", announcementId);
    }
}
