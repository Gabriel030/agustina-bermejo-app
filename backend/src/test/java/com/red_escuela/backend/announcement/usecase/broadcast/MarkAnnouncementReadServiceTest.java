package com.red_escuela.backend.announcement.usecase.broadcast;

import com.red_escuela.backend.announcement.specialist.AnnouncementReadRecorder;
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
