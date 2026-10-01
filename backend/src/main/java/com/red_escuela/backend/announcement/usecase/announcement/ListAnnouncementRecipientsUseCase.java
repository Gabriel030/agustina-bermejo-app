package com.red_escuela.backend.announcement.usecase.announcement;

import com.red_escuela.backend.announcement.dto.AnnouncementRecipientResponse;

import java.util.List;

public interface ListAnnouncementRecipientsUseCase {

    List<AnnouncementRecipientResponse> list(Long announcementId);
}
