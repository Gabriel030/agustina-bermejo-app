package com.red_escuela.backend.announcement.usecase.announcement;

import com.red_escuela.backend.announcement.exception.AnnouncementNotFoundException;
import com.red_escuela.backend.announcement.service.AnnouncementReadRecorder;
import com.red_escuela.backend.announcement.service.AnnouncementViewer;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class MarkAnnouncementReadService implements MarkAnnouncementReadUseCase {

    private final AnnouncementViewer announcementViewer;
    private final AnnouncementReadRecorder readRecorder;

    public MarkAnnouncementReadService(AnnouncementViewer announcementViewer, AnnouncementReadRecorder readRecorder) {
        this.announcementViewer = announcementViewer;
        this.readRecorder = readRecorder;
    }

    @Override
    public void markRead(Long announcementId) {
        Long studentId = announcementViewer.currentStudentId()
                .orElseThrow(() -> new AnnouncementNotFoundException(announcementId));
        readRecorder.recordRead(announcementId, studentId);
    }
}
