package com.red_escuela.backend.announcement.usecase.announcement;

import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.announcement.entity.Announcement;
import com.red_escuela.backend.announcement.exception.AnnouncementNotFoundException;
import com.red_escuela.backend.announcement.repository.AnnouncementRepository;
import com.red_escuela.backend.announcement.service.SectionMembership;
import com.red_escuela.backend.notification.repository.NotificationRepository;
import com.red_escuela.backend.service.CurrentInstructorService;
import com.red_escuela.backend.shared.UseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class DeleteAnnouncementService implements DeleteAnnouncementUseCase {

    private static final Logger log = LoggerFactory.getLogger(DeleteAnnouncementService.class);
    private static final String RELATED_ENTITY_TYPE = "Announcement";

    private final AnnouncementRepository announcementRepository;
    private final NotificationRepository notificationRepository;
    private final SectionMembership sectionMembership;
    private final CurrentInstructorService currentInstructorService;

    public DeleteAnnouncementService(AnnouncementRepository announcementRepository,
                                     NotificationRepository notificationRepository,
                                     SectionMembership sectionMembership,
                                     CurrentInstructorService currentInstructorService) {
        this.announcementRepository = announcementRepository;
        this.notificationRepository = notificationRepository;
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

        int removed = notificationRepository.deleteUnreadByRelatedEntity(RELATED_ENTITY_TYPE, announcementId);
        announcementRepository.delete(announcement);

        log.info("Aviso eliminado: id={}, punteros no leídos borrados={}", announcementId, removed);
    }
}
