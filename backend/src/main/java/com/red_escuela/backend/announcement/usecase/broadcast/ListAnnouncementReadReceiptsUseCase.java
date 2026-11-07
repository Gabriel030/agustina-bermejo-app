package com.red_escuela.backend.announcement.usecase.broadcast;

import com.red_escuela.backend.announcement.dto.AnnouncementRecipientResponse;

import java.util.List;

public interface ListAnnouncementReadReceiptsUseCase {

    List<AnnouncementRecipientResponse> list(Long announcementId);
}
