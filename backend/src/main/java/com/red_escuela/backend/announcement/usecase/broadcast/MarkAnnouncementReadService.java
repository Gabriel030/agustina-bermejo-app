package com.red_escuela.backend.announcement.usecase.broadcast;

import com.red_escuela.backend.announcement.specialist.AnnouncementReadRecorder;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class MarkAnnouncementReadService implements MarkAnnouncementReadUseCase {

    private final AnnouncementReadRecorder readRecorder;

    public MarkAnnouncementReadService(AnnouncementReadRecorder readRecorder) {
        this.readRecorder = readRecorder;
    }

    @Override
    public void markRead(Long announcementId) {
        readRecorder.recordReadByCurrentStudent(announcementId);
    }
}
