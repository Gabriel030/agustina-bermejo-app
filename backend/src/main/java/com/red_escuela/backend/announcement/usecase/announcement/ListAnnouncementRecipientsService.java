package com.red_escuela.backend.announcement.usecase.announcement;

import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.announcement.dto.AnnouncementRecipientResponse;
import com.red_escuela.backend.announcement.entity.Announcement;
import com.red_escuela.backend.announcement.exception.AnnouncementNotFoundException;
import com.red_escuela.backend.announcement.repository.AnnouncementRecipientRepository;
import com.red_escuela.backend.announcement.repository.AnnouncementRepository;
import com.red_escuela.backend.announcement.service.SectionMembership;
import com.red_escuela.backend.service.CurrentInstructorService;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@UseCase
public class ListAnnouncementRecipientsService implements ListAnnouncementRecipientsUseCase {

    private final AnnouncementRepository announcementRepository;
    private final AnnouncementRecipientRepository recipientRepository;
    private final SectionMembership sectionMembership;
    private final CurrentInstructorService currentInstructorService;

    public ListAnnouncementRecipientsService(AnnouncementRepository announcementRepository,
                                             AnnouncementRecipientRepository recipientRepository,
                                             SectionMembership sectionMembership,
                                             CurrentInstructorService currentInstructorService) {
        this.announcementRepository = announcementRepository;
        this.recipientRepository = recipientRepository;
        this.sectionMembership = sectionMembership;
        this.currentInstructorService = currentInstructorService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AnnouncementRecipientResponse> list(Long announcementId) {
        Announcement announcement = announcementRepository.findById(announcementId)
                .orElseThrow(() -> new AnnouncementNotFoundException(announcementId));

        Instructor instructor = currentInstructorService.resolveCurrentInstructor();
        sectionMembership.requireInstructorOf(announcement.getSectionType(), announcement.getSectionId(), instructor.getId());

        return recipientRepository.findByAnnouncementId(announcementId).stream()
                .map(AnnouncementRecipientResponse::from)
                .sorted(Comparator.comparing(AnnouncementRecipientResponse::name))
                .toList();
    }
}
