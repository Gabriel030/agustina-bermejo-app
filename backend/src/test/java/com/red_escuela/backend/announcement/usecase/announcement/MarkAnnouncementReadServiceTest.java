package com.red_escuela.backend.announcement.usecase.announcement;

import com.red_escuela.backend.announcement.service.AnnouncementReadRecorder;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MarkAnnouncementReadServiceTest {

    private final AnnouncementReadRecorder readRecorder = mock(AnnouncementReadRecorder.class);

    @Test
    void delegatesTheReadOfTheCurrentStudentToTheRecorder() {
        new MarkAnnouncementReadService(readRecorder).markRead(9L);

        verify(readRecorder).recordReadByCurrentStudent(9L);
    }
}
