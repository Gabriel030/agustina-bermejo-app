package com.red_escuela.backend.announcement.usecase.broadcast;

import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.announcement.dto.AnnouncementRecipientResponse;
import com.red_escuela.backend.announcement.entity.Announcement;
import com.red_escuela.backend.announcement.entity.SectionType;
import com.red_escuela.backend.announcement.exception.NotSectionInstructorException;
import com.red_escuela.backend.announcement.repository.AnnouncementRepository;
import com.red_escuela.backend.announcement.specialist.AnnouncementReadReceiptReader;
import com.red_escuela.backend.announcement.specialist.AnnouncementRecipientReader;
import com.red_escuela.backend.announcement.specialist.AnnouncementRecipientReader.RecipientSummary;
import com.red_escuela.backend.announcement.specialist.SectionMembershipChecker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ListAnnouncementRecipientsServiceTest {

    private static final LocalDateTime READ_AT = LocalDateTime.of(2026, 11, 6, 9, 30);

    private final AnnouncementRepository announcementRepository = mock(AnnouncementRepository.class);
    private final AnnouncementRecipientReader recipientReader = mock(AnnouncementRecipientReader.class);
    private final SectionMembershipChecker sectionMembership = mock(SectionMembershipChecker.class);
    private final ListAnnouncementRecipientsService instructorView =
            new ListAnnouncementRecipientsService(announcementRepository, recipientReader, sectionMembership);

    private Announcement announcement;

    @BeforeEach
    void setUp() {
        SubjectSection section = new SubjectSection();
        section.setId(30L);
        announcement = Announcement.builder().id(7L).subjectSection(section).build();
    }

    @Test
    void hidesReadAtFromTheInstructorAfterCheckingMembership() {
        when(announcementRepository.findById(7L)).thenReturn(Optional.of(announcement));
        when(recipientReader.listRecipients(7L)).thenReturn(List.of(new RecipientSummary(1L, "A-1", "Ana Paz", READ_AT)));

        List<AnnouncementRecipientResponse> result = instructorView.list(7L);

        assertThat(result).singleElement().satisfies(recipient -> assertThat(recipient.readAt()).isNull());
        verify(sectionMembership).requireCurrentInstructorOf(SectionType.PROGRAM, 30L);
    }

    @Test
    void rejectsAnInstructorOfAnotherSection() {
        when(announcementRepository.findById(7L)).thenReturn(Optional.of(announcement));
        doThrow(new NotSectionInstructorException(30L)).when(sectionMembership)
                .requireCurrentInstructorOf(SectionType.PROGRAM, 30L);

        assertThatThrownBy(() -> instructorView.list(7L)).isInstanceOf(NotSectionInstructorException.class);
        verifyNoInteractions(recipientReader);
    }

    @Test
    void showsReadAtToStaffThroughTheReadReceiptsView() {
        AnnouncementReadReceiptReader receiptReader = mock(AnnouncementReadReceiptReader.class);
        when(receiptReader.listReceipts(7L)).thenReturn(List.of(new RecipientSummary(1L, "A-1", "Ana Paz", READ_AT)));

        List<AnnouncementRecipientResponse> result = new ListAnnouncementReadReceiptsService(receiptReader).list(7L);

        assertThat(result).singleElement().satisfies(recipient -> assertThat(recipient.readAt()).isEqualTo(READ_AT));
        verifyNoInteractions(sectionMembership);
    }
}
