package com.red_escuela.backend.announcement.specialist;

import com.red_escuela.backend.announcement.repository.AnnouncementRecipientRepository;
import com.red_escuela.backend.announcement.repository.AnnouncementRecipientRepository.ReadTime;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class StudentAnnouncementReadTimeReaderTest {

    private static final LocalDateTime READ_AT = LocalDateTime.of(2026, 11, 6, 9, 0);

    private final AnnouncementViewer viewer = mock(AnnouncementViewer.class);
    private final AnnouncementRecipientRepository repository = mock(AnnouncementRecipientRepository.class);
    private final StudentAnnouncementReadTimeReader readTimes = new StudentAnnouncementReadTimeReader(viewer, repository);

    @Test
    void mapsEachReadAnnouncementToItsReadTime() {
        ReadTime row = mock(ReadTime.class);
        when(row.getAnnouncementId()).thenReturn(3L);
        when(row.getReadAt()).thenReturn(READ_AT);
        when(viewer.currentStudentId()).thenReturn(Optional.of(5L));
        when(repository.findReadTimes(5L, List.of(3L, 4L))).thenReturn(List.of(row));

        assertThat(readTimes.readTimesOfCurrentStudent(List.of(3L, 4L))).containsOnly(java.util.Map.entry(3L, READ_AT));
    }

    @Test
    void returnsNothingWhenThePersonIsNotAStudent() {
        when(viewer.currentStudentId()).thenReturn(Optional.empty());

        assertThat(readTimes.readTimesOfCurrentStudent(List.of(3L))).isEmpty();
        verifyNoInteractions(repository);
    }
}
