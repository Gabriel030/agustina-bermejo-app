package com.red_escuela.backend.announcement.specialist;

import com.red_escuela.backend.announcement.exception.AnnouncementNotFoundException;
import com.red_escuela.backend.announcement.repository.AnnouncementRecipientRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnnouncementReadRecorderTest {

    private final AnnouncementRecipientRepository recipientRepository = mock(AnnouncementRecipientRepository.class);
    private final AnnouncementViewer announcementViewer = mock(AnnouncementViewer.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final AnnouncementReadRecorder recorder = new AnnouncementReadRecorder(recipientRepository, announcementViewer,
            Clock.fixed(Instant.parse("2026-11-06T12:00:00Z"), ZoneOffset.UTC), eventPublisher);

    @Test
    void recordsTheReadAtTheCurrentInstant() {
        when(recipientRepository.markReadIfUnread(any(), any(), any())).thenReturn(1);
        recorder.recordRead(9L, 5L);

        verify(recipientRepository).markReadIfUnread(9L, 5L, LocalDateTime.of(2026, 11, 6, 12, 0));
    }

    @Test
    void doesNotFailWhenTheReadWasAlreadyRecorded() {
        when(recipientRepository.markReadIfUnread(any(), any(), any())).thenReturn(0);
        when(recipientRepository.existsByAnnouncementIdAndStudentId(9L, 5L)).thenReturn(true);

        recorder.recordRead(9L, 5L);
    }

    @Test
    void answersNotFoundWhenNothingWasUpdatedAndTheStudentIsNotARecipient() {
        when(recipientRepository.markReadIfUnread(any(), any(), any())).thenReturn(0);
        when(recipientRepository.existsByAnnouncementIdAndStudentId(9L, 5L)).thenReturn(false);

        assertThatThrownBy(() -> recorder.recordRead(9L, 5L)).isInstanceOf(AnnouncementNotFoundException.class);
    }

    @Test
    void answersNotFoundWhenTheCurrentPersonIsNotAStudent() {
        when(announcementViewer.currentStudentId()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> recorder.recordReadByCurrentStudent(9L)).isInstanceOf(AnnouncementNotFoundException.class);
        verify(recipientRepository, never()).markReadIfUnread(any(), any(), any());
    }

    @Test
    void recordsTheReadOfTheStudentLinkedToTheCurrentPerson() {
        when(announcementViewer.currentStudentId()).thenReturn(Optional.of(5L));
        when(recipientRepository.markReadIfUnread(any(), any(), any())).thenReturn(1);

        recorder.recordReadByCurrentStudent(9L);

        verify(recipientRepository).markReadIfUnread(9L, 5L, LocalDateTime.of(2026, 11, 6, 12, 0));
    }

    @Test
    void publishesTheReadEventWithTheCurrentPersonOnly() {
        when(announcementViewer.currentStudentId()).thenReturn(Optional.of(5L));
        when(announcementViewer.currentPersonId()).thenReturn(77L);
        when(recipientRepository.markReadIfUnread(any(), any(), any())).thenReturn(1);

        recorder.recordReadByCurrentStudent(9L);

        org.mockito.ArgumentCaptor<Object> captor = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        com.red_escuela.backend.announcement.events.AnnouncementReadEvent event = (com.red_escuela.backend.announcement.events.AnnouncementReadEvent) captor.getValue();
        org.assertj.core.api.Assertions.assertThat(event.announcementId()).isEqualTo(9L);
        org.assertj.core.api.Assertions.assertThat(event.readerPersonId()).isEqualTo(77L);
    }
}
