package com.red_escuela.backend.announcement.specialist;

import com.red_escuela.backend.announcement.entity.Announcement;
import com.red_escuela.backend.announcement.exception.AnnouncementNotFoundException;
import com.red_escuela.backend.announcement.repository.AnnouncementRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AnnouncementReadReceiptReader {

    private final AnnouncementRepository announcementRepository;
    private final AnnouncementRecipientReader recipientReader;
    private final SectionMembershipChecker sectionMembership;

    public AnnouncementReadReceiptReader(AnnouncementRepository announcementRepository,
                                         AnnouncementRecipientReader recipientReader,
                                         SectionMembershipChecker sectionMembership) {
        this.announcementRepository = announcementRepository;
        this.recipientReader = recipientReader;
        this.sectionMembership = sectionMembership;
    }

    public List<AnnouncementRecipientReader.RecipientSummary> listReceiptsForSectionInstructor(Long announcementId) {
        Announcement announcement = announcementRepository.findById(announcementId)
                .orElseThrow(() -> new AnnouncementNotFoundException(announcementId));
        sectionMembership.requireCurrentInstructorOf(announcement.getSectionType(), announcement.getSectionId());
        return recipientReader.listRecipients(announcementId);
    }

    public List<AnnouncementRecipientReader.RecipientSummary> listReceipts(Long announcementId) {
        if (!announcementRepository.existsById(announcementId)) {
            throw new AnnouncementNotFoundException(announcementId);
        }
        return recipientReader.listRecipients(announcementId);
    }
}
