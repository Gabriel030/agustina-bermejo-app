package com.red_escuela.backend.announcement.service;

import com.red_escuela.backend.announcement.exception.AnnouncementNotFoundException;
import com.red_escuela.backend.announcement.repository.AnnouncementRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AnnouncementReadReceiptReader {

    private final AnnouncementRepository announcementRepository;
    private final AnnouncementRecipientReader recipientReader;

    public AnnouncementReadReceiptReader(AnnouncementRepository announcementRepository,
                                         AnnouncementRecipientReader recipientReader) {
        this.announcementRepository = announcementRepository;
        this.recipientReader = recipientReader;
    }

    public List<AnnouncementRecipientReader.RecipientSummary> listReceipts(Long announcementId) {
        if (!announcementRepository.existsById(announcementId)) {
            throw new AnnouncementNotFoundException(announcementId);
        }
        return recipientReader.listRecipients(announcementId);
    }
}
