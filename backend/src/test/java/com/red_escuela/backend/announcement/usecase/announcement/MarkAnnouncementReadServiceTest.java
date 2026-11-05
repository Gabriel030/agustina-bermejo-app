package com.red_escuela.backend.announcement.usecase.announcement;

import com.red_escuela.backend.announcement.exception.AnnouncementNotFoundException;
import com.red_escuela.backend.announcement.service.AnnouncementReadRecorder;
import com.red_escuela.backend.announcement.service.AnnouncementViewer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarkAnnouncementReadServiceTest {

    @Mock
    private AnnouncementViewer announcementViewer;
    @Mock
    private AnnouncementReadRecorder readRecorder;
    @InjectMocks
    private MarkAnnouncementReadService service;

    @Test
    void recordsTheReadForTheStudentOfTheCurrentPerson() {
        when(announcementViewer.currentStudentId()).thenReturn(Optional.of(5L));

        service.markRead(9L);

        verify(readRecorder).recordRead(9L, 5L);
    }

    @Test
    void answersNotFoundWhenTheCurrentPersonIsNotAStudent() {
        when(announcementViewer.currentStudentId()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead(9L)).isInstanceOf(AnnouncementNotFoundException.class);
        verifyNoInteractions(readRecorder);
    }
}
