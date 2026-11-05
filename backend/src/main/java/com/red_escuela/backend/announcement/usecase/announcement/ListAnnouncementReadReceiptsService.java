package com.red_escuela.backend.announcement.usecase.announcement;

import com.red_escuela.backend.announcement.dto.AnnouncementRecipientResponse;
import com.red_escuela.backend.announcement.service.AnnouncementReadReceiptReader;
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
        return receiptReader.listReceipts(announcementId).stream()
                .map(recipient -> new AnnouncementRecipientResponse(recipient.studentId(), recipient.file(),
                        recipient.fullName(), recipient.readAt()))
                .toList();
    }
}
