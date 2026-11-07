package com.red_escuela.backend.announcement.specialist;

import com.red_escuela.backend.announcement.repository.AnnouncementRecipientRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Component
public class AnnouncementRecipientReader {

    public record RecipientSummary(Long studentId, String file, String fullName, LocalDateTime readAt) {
    }

    private final AnnouncementRecipientRepository recipientRepository;

    public AnnouncementRecipientReader(AnnouncementRecipientRepository recipientRepository) {
        this.recipientRepository = recipientRepository;
    }

    @Transactional(readOnly = true)
    public List<RecipientSummary> listRecipients(Long announcementId) {
        return recipientRepository.findByAnnouncementId(announcementId).stream()
                .map(recipient -> new RecipientSummary(
                        recipient.getStudent().getId(),
                        recipient.getStudent().getPerson().getFile(),
                        recipient.getStudent().getPerson().getName() + " " + recipient.getStudent().getPerson().getLastname(),
                        recipient.getReadAt()))
                .sorted(Comparator.comparing(RecipientSummary::fullName))
                .toList();
    }
}
