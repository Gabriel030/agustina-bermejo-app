package com.red_escuela.backend.announcement.usecase.broadcast;

import com.red_escuela.backend.announcement.dto.AnnouncementRecipientResponse;
import com.red_escuela.backend.announcement.specialist.AnnouncementReadReceiptReader;
import com.red_escuela.backend.announcement.specialist.AnnouncementRecipientReader;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ListAnnouncementReadReceiptsService implements ListAnnouncementReadReceiptsUseCase {

    private final AnnouncementReadReceiptReader receiptReader;

    public ListAnnouncementReadReceiptsService(AnnouncementReadReceiptReader receiptReader) {
        this.receiptReader = receiptReader;
    }

    @Override
    public List<AnnouncementRecipientResponse> list(Long announcementId) {
        return toResponses(receiptReader.listReceipts(announcementId));
    }

    @Override
    public List<AnnouncementRecipientResponse> listAsSectionInstructor(Long announcementId) {
        return toResponses(receiptReader.listReceiptsForSectionInstructor(announcementId));
    }

    private static List<AnnouncementRecipientResponse> toResponses(List<AnnouncementRecipientReader.RecipientSummary> recipients) {
        return recipients.stream()
                .map(recipient -> new AnnouncementRecipientResponse(recipient.studentId(), recipient.file(),
                        recipient.fullName(), recipient.readAt()))
                .toList();
    }
}
