package com.red_escuela.backend.announcement.usecase.announcement;

import com.red_escuela.backend.announcement.dto.AnnouncementRecipientResponse;
import com.red_escuela.backend.announcement.entity.Announcement;
import com.red_escuela.backend.announcement.entity.SectionType;
import com.red_escuela.backend.announcement.exception.NotSectionInstructorException;
import com.red_escuela.backend.announcement.repository.AnnouncementRepository;
import com.red_escuela.backend.announcement.service.AnnouncementRecipientReader;
import com.red_escuela.backend.announcement.service.AnnouncementRecipientReader.RecipientSummary;
import com.red_escuela.backend.announcement.service.SectionMembership;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListAnnouncementRecipientsServiceTest {

    private static final LocalDateTime READ_AT = LocalDateTime.of(2026, 11, 6, 9, 30);

    @Mock
    private AnnouncementRepository announcementRepository;
    @Mock
    private AnnouncementRecipientReader recipientReader;
    @Mock
    private SectionMembership sectionMembership;
    @InjectMocks
    private ListAnnouncementRecipientsService service;

    private Announcement announcement;

    @BeforeEach
    void setUp() {
        SubjectSection section = new SubjectSection();
        section.setId(30L);
        announcement = Announcement.builder().id(7L).subjectSection(section).build();
    }

    private void givenOneReadRecipient() {
        when(announcementRepository.findById(7L)).thenReturn(Optional.of(announcement));
        when(recipientReader.listRecipients(7L)).thenReturn(List.of(new RecipientSummary(1L, "A-1", "Ana Paz", READ_AT)));
    }

    @Test
    void hidesReadAtFromTheInstructorAfterCheckingMembership() {
        givenOneReadRecipient();

        List<AnnouncementRecipientResponse> result = service.list(new ListAnnouncementRecipientsCommand(7L, true));

        assertThat(result).singleElement().satisfies(recipient -> assertThat(recipient.readAt()).isNull());
        verify(sectionMembership).requireCurrentInstructorOf(SectionType.PROGRAM, 30L);
    }

    @Test
    void showsReadAtToStaffWithoutCheckingMembership() {
        givenOneReadRecipient();

        List<AnnouncementRecipientResponse> result = service.list(new ListAnnouncementRecipientsCommand(7L, false));

        assertThat(result).singleElement().satisfies(recipient -> assertThat(recipient.readAt()).isEqualTo(READ_AT));
        verifyNoInteractions(sectionMembership);
    }

    @Test
    void rejectsAnInstructorOfAnotherSection() {
        when(announcementRepository.findById(7L)).thenReturn(Optional.of(announcement));
        doThrow(new NotSectionInstructorException(30L)).when(sectionMembership)
                .requireCurrentInstructorOf(SectionType.PROGRAM, 30L);

        assertThatThrownBy(() -> service.list(new ListAnnouncementRecipientsCommand(7L, true)))
                .isInstanceOf(NotSectionInstructorException.class);
        verifyNoInteractions(recipientReader);
    }
}
