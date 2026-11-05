package com.red_escuela.backend.announcement.usecase.announcement;

import com.red_escuela.backend.announcement.dto.AnnouncementRecipientResponse;
import com.red_escuela.backend.announcement.entity.Announcement;
import com.red_escuela.backend.announcement.exception.AnnouncementNotFoundException;
import com.red_escuela.backend.announcement.repository.AnnouncementRepository;
import com.red_escuela.backend.announcement.service.AnnouncementRecipientReader;
import com.red_escuela.backend.announcement.service.SectionMembership;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ListAnnouncementRecipientsService implements ListAnnouncementRecipientsUseCase {

    private final AnnouncementRepository announcementRepository;
    private final AnnouncementRecipientReader recipientReader;
    private final SectionMembership sectionMembership;

    public ListAnnouncementRecipientsService(AnnouncementRepository announcementRepository,
                                             AnnouncementRecipientReader recipientReader,
                                             SectionMembership sectionMembership) {
        this.announcementRepository = announcementRepository;
        this.recipientReader = recipientReader;
        this.sectionMembership = sectionMembership;
    }

    @Override
    public List<AnnouncementRecipientResponse> list(ListAnnouncementRecipientsCommand command) {
        Announcement announcement = announcementRepository.findById(command.announcementId())
                .orElseThrow(() -> new AnnouncementNotFoundException(command.announcementId()));

        if (command.instructorView()) {
            sectionMembership.requireCurrentInstructorOf(announcement.getSectionType(), announcement.getSectionId());
        }

        return recipientReader.listRecipients(command.announcementId()).stream()
                .map(recipient -> new AnnouncementRecipientResponse(recipient.studentId(), recipient.file(), recipient.fullName(),
                        command.instructorView() ? null : recipient.readAt()))
                .toList();
    }
}
