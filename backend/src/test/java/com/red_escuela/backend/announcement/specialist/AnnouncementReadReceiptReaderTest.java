package com.red_escuela.backend.announcement.specialist;

import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.announcement.entity.Announcement;
import com.red_escuela.backend.announcement.entity.SectionType;
import com.red_escuela.backend.announcement.exception.AnnouncementNotFoundException;
import com.red_escuela.backend.announcement.exception.NotSectionInstructorException;
import com.red_escuela.backend.announcement.repository.AnnouncementRepository;
import com.red_escuela.backend.announcement.specialist.AnnouncementRecipientReader.RecipientSummary;
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

class AnnouncementReadReceiptReaderTest {

    private static final LocalDateTime READ_AT = LocalDateTime.of(2026, 11, 6, 9, 30);

    private final AnnouncementRepository announcementRepository = mock(AnnouncementRepository.class);
    private final AnnouncementRecipientReader recipientReader = mock(AnnouncementRecipientReader.class);
    private final SectionMembershipChecker sectionMembership = mock(SectionMembershipChecker.class);
    private final AnnouncementReadReceiptReader reader =
            new AnnouncementReadReceiptReader(announcementRepository, recipientReader, sectionMembership);

    private Announcement announcement;

    @BeforeEach
    void setUp() {
        SubjectSection section = new SubjectSection();
        section.setId(30L);
        announcement = Announcement.builder().id(7L).subjectSection(section).build();
    }

    @Test
    void checksSectionMembershipBeforeListingReceiptsForAnInstructor() {
        when(announcementRepository.findById(7L)).thenReturn(Optional.of(announcement));
        when(recipientReader.listRecipients(7L)).thenReturn(List.of(new RecipientSummary(1L, "A-1", "Ana Paz", READ_AT)));

        assertThat(reader.listReceiptsForSectionInstructor(7L)).singleElement()
                .satisfies(recipient -> assertThat(recipient.readAt()).isEqualTo(READ_AT));
        verify(sectionMembership).requireCurrentInstructorOf(SectionType.PROGRAM, 30L);
    }

    @Test
    void rejectsReceiptsForAnInstructorOfAnotherSection() {
        when(announcementRepository.findById(7L)).thenReturn(Optional.of(announcement));
        doThrow(new NotSectionInstructorException(30L)).when(sectionMembership)
                .requireCurrentInstructorOf(SectionType.PROGRAM, 30L);

        assertThatThrownBy(() -> reader.listReceiptsForSectionInstructor(7L)).isInstanceOf(NotSectionInstructorException.class);
        verifyNoInteractions(recipientReader);
    }

    @Test
    void rejectsReceiptsForAnAnnouncementThatDoesNotExist() {
        when(announcementRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reader.listReceiptsForSectionInstructor(7L)).isInstanceOf(AnnouncementNotFoundException.class);
        verifyNoInteractions(sectionMembership, recipientReader);
    }
}
