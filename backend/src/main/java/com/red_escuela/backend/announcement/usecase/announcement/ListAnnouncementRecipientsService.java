package com.red_escuela.backend.announcement.usecase.announcement;

import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.announcement.dto.AnnouncementRecipientResponse;
import com.red_escuela.backend.announcement.entity.Announcement;
import com.red_escuela.backend.announcement.exception.AnnouncementNotFoundException;
import com.red_escuela.backend.announcement.repository.AnnouncementRepository;
import com.red_escuela.backend.announcement.service.AnnouncementRecipientReader;
import com.red_escuela.backend.announcement.service.SectionMembership;
import com.red_escuela.backend.academicstructure.service.CurrentInstructorService;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ListAnnouncementRecipientsService implements ListAnnouncementRecipientsUseCase {

    private final AnnouncementRepository announcementRepository;
    private final AnnouncementRecipientReader recipientReader;
    private final SectionMembership sectionMembership;
    private final CurrentInstructorService currentInstructorService;

    public ListAnnouncementRecipientsService(AnnouncementRepository announcementRepository,
                                             AnnouncementRecipientReader recipientReader,
                                             SectionMembership sectionMembership,
                                             CurrentInstructorService currentInstructorService) {
        this.announcementRepository = announcementRepository;
        this.recipientReader = recipientReader;
        this.sectionMembership = sectionMembership;
        this.currentInstructorService = currentInstructorService;
    }

    @Override
    public List<AnnouncementRecipientResponse> list(Long announcementId) {
        Announcement announcement = announcementRepository.findById(announcementId)
                .orElseThrow(() -> new AnnouncementNotFoundException(announcementId));

        Instructor instructor = currentInstructorService.resolveCurrentInstructor();
        sectionMembership.requireInstructorOf(announcement.getSectionType(), announcement.getSectionId(), instructor.getId());

        return recipientReader.listRecipients(announcementId).stream()
                .map(recipient -> new AnnouncementRecipientResponse(recipient.studentId(), recipient.file(), recipient.fullName()))
                .toList();
    }
}
