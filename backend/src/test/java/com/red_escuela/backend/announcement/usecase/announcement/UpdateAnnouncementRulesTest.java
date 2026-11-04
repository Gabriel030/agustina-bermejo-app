package com.red_escuela.backend.announcement.usecase.announcement;

import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.service.CurrentInstructorService;
import com.red_escuela.backend.announcement.entity.Announcement;
import com.red_escuela.backend.announcement.entity.AnnouncementCategory;
import com.red_escuela.backend.announcement.entity.AnnouncementRecipient;
import com.red_escuela.backend.announcement.entity.AnnouncementStatus;
import com.red_escuela.backend.announcement.entity.AudienceSegmentKey;
import com.red_escuela.backend.announcement.entity.AudienceSpec;
import com.red_escuela.backend.announcement.entity.SectionType;
import com.red_escuela.backend.announcement.events.AnnouncementUpdatedEvent;
import com.red_escuela.backend.announcement.exception.AnnouncementAlreadySentException;
import com.red_escuela.backend.announcement.exception.AnnouncementNotFoundException;
import com.red_escuela.backend.announcement.exception.CannotChangeAudienceOfSentAnnouncementException;
import com.red_escuela.backend.announcement.exception.NotSectionInstructorException;
import com.red_escuela.backend.announcement.exception.ScheduledDateInThePastException;
import com.red_escuela.backend.announcement.repository.AnnouncementRecipientRepository;
import com.red_escuela.backend.announcement.repository.AnnouncementRepository;
import com.red_escuela.backend.announcement.service.SectionMembership;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateAnnouncementRulesTest {

    @Mock
    private AnnouncementRepository announcementRepository;
    @Mock
    private AnnouncementRecipientRepository recipientRepository;
    @Mock
    private SectionMembership sectionMembership;
    @Mock
    private CurrentInstructorService currentInstructorService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private UpdateAnnouncementService service;
    private SubjectSection section;

    @BeforeEach
    void setUp() {
        service = new UpdateAnnouncementService(announcementRepository, recipientRepository, sectionMembership,
                currentInstructorService, eventPublisher);
        Subject subject = new Subject();
        subject.setName("Matematica");
        section = new SubjectSection();
        section.setId(60L);
        section.setSubject(subject);
    }

    private Announcement announcement(AnnouncementStatus status) {
        LocalDateTime future = LocalDateTime.now().plusDays(3);
        return Announcement.builder()
                .id(1L)
                .subjectSection(section)
                .title("Titulo original")
                .body("Cuerpo original")
                .category(AnnouncementCategory.REMINDER)
                .urgent(false)
                .audienceSpec(AudienceSpec.wholeSection())
                .audienceLabel("Toda la comisión")
                .status(status)
                .scheduledFor(status == AnnouncementStatus.SCHEDULED ? future : null)
                .sentAt(status == AnnouncementStatus.SENT ? LocalDateTime.now().minusDays(1) : null)
                .build();
    }

    private void givenInstructorAndAnnouncement(Announcement announcement) {
        when(announcementRepository.findById(1L)).thenReturn(java.util.Optional.of(announcement));
        Instructor instructor = new Instructor();
        instructor.setId(5L);
        when(currentInstructorService.resolveCurrentInstructor()).thenReturn(instructor);
    }

    private static UpdateAnnouncementCommand command(String title, String body, LocalDateTime scheduledFor, AudienceSpec spec) {
        return command(title, body, scheduledFor, spec, false);
    }

    private static UpdateAnnouncementCommand command(String title, String body, LocalDateTime scheduledFor, AudienceSpec spec,
                                                     boolean notify) {
        return new UpdateAnnouncementCommand(1L, title, body, null, true, LocalDate.of(2026, 12, 31), scheduledFor, spec, notify);
    }

    private static Student studentWithPerson(long personId) {
        Person person = new Person();
        person.setId(personId);
        Student student = new Student();
        student.setPerson(person);
        return student;
    }

    @Test
    void updateFailsWhenAnnouncementDoesNotExist() {
        when(announcementRepository.findById(1L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.update(command("t", null, null, null))).isInstanceOf(AnnouncementNotFoundException.class);
    }

    @Test
    void updateIsForbiddenForInstructorsWhoDoNotTeachTheSection() {
        Announcement sent = announcement(AnnouncementStatus.SENT);
        givenInstructorAndAnnouncement(sent);
        doThrow(new NotSectionInstructorException(60L)).when(sectionMembership).requireInstructorOf(SectionType.PROGRAM, 60L, 5L);

        assertThatThrownBy(() -> service.update(command("Nuevo", null, null, null))).isInstanceOf(NotSectionInstructorException.class);
        assertThat(sent.getTitle()).isEqualTo("Titulo original");
        verify(announcementRepository, never()).save(any());
    }

    @Test
    void sentAnnouncementCannotChangeItsAudience() {
        givenInstructorAndAnnouncement(announcement(AnnouncementStatus.SENT));

        assertThatThrownBy(() -> service.update(command(null, null, null, new AudienceSpec(AudienceSegmentKey.PASSED, null, null, null))))
                .isInstanceOf(CannotChangeAudienceOfSentAnnouncementException.class);
        verify(announcementRepository, never()).save(any());
    }

    @Test
    void sentAnnouncementCannotBeRescheduled() {
        givenInstructorAndAnnouncement(announcement(AnnouncementStatus.SENT));

        assertThatThrownBy(() -> service.update(command(null, null, LocalDateTime.now().plusDays(5), null)))
                .isInstanceOf(AnnouncementAlreadySentException.class);
        verify(announcementRepository, never()).save(any());
    }

    @Test
    void scheduledAnnouncementCannotBeMovedToThePast() {
        Announcement scheduled = announcement(AnnouncementStatus.SCHEDULED);
        givenInstructorAndAnnouncement(scheduled);
        LocalDateTime originalDate = scheduled.getScheduledFor();

        assertThatThrownBy(() -> service.update(command(null, null, LocalDateTime.now().minusMinutes(1), null)))
                .isInstanceOf(ScheduledDateInThePastException.class);
        assertThat(scheduled.getScheduledFor()).isEqualTo(originalDate);
        verify(announcementRepository, never()).save(any());
    }

    @Test
    void scheduledAnnouncementChangesDateAndAudienceWithoutNotifyingAnyone() {
        Announcement scheduled = announcement(AnnouncementStatus.SCHEDULED);
        givenInstructorAndAnnouncement(scheduled);
        when(announcementRepository.save(scheduled)).thenReturn(scheduled);
        LocalDateTime newDate = LocalDateTime.now().plusDays(10);
        AudienceSpec newSpec = new AudienceSpec(AudienceSegmentKey.PASSED, null, null, null);

        Announcement saved = service.update(command("Titulo nuevo", "Cuerpo nuevo", newDate, newSpec));

        assertThat(saved.getTitle()).isEqualTo("Titulo nuevo");
        assertThat(saved.getBody()).isEqualTo("Cuerpo nuevo");
        assertThat(saved.getScheduledFor()).isEqualTo(newDate);
        assertThat(saved.getAudienceSpec()).isSameAs(newSpec);
        assertThat(saved.getAudienceLabel()).isEqualTo("Ya aprobaron la materia");
        assertThat(saved.isUrgent()).isTrue();
        assertThat(saved.getExpiresAt()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(saved.getUpdatedAt()).isNotNull();
        verify(eventPublisher, never()).publishEvent(any(Object.class));
        verify(recipientRepository, never()).findByAnnouncementId(any());
    }

    @Test
    void scheduledAnnouncementKeepsDateAndAudienceWhenTheyAreNotProvided() {
        Announcement scheduled = announcement(AnnouncementStatus.SCHEDULED);
        givenInstructorAndAnnouncement(scheduled);
        when(announcementRepository.save(scheduled)).thenReturn(scheduled);
        LocalDateTime originalDate = scheduled.getScheduledFor();

        Announcement saved = service.update(command(null, null, null, null));

        assertThat(saved.getTitle()).isEqualTo("Titulo original");
        assertThat(saved.getBody()).isEqualTo("Cuerpo original");
        assertThat(saved.getScheduledFor()).isEqualTo(originalDate);
        assertThat(saved.getAudienceSpec().isWholeSection()).isTrue();
    }

    @Test
    void sentAnnouncementEditWithoutNotifyDoesNotNotifyAnyone() {
        Announcement sent = announcement(AnnouncementStatus.SENT);
        givenInstructorAndAnnouncement(sent);
        when(announcementRepository.save(sent)).thenReturn(sent);

        Announcement saved = service.update(command("Titulo corregido", null, null, null, false));

        assertThat(saved.getTitle()).isEqualTo("Titulo corregido");
        verify(eventPublisher, never()).publishEvent(any(Object.class));
        verify(recipientRepository, never()).findByAnnouncementId(any());
    }

    @Test
    void sentAnnouncementEditWithNotifyRenotifiesTheSameFrozenRecipients() {
        Announcement sent = announcement(AnnouncementStatus.SENT);
        givenInstructorAndAnnouncement(sent);
        when(announcementRepository.save(sent)).thenReturn(sent);
        when(recipientRepository.findByAnnouncementId(1L)).thenReturn(List.of(
                AnnouncementRecipient.builder().student(studentWithPerson(101L)).build(),
                AnnouncementRecipient.builder().student(studentWithPerson(102L)).build()));

        Announcement saved = service.update(command("Titulo corregido", null, null, null, true));

        assertThat(saved.getTitle()).isEqualTo("Titulo corregido");
        assertThat(saved.getStatus()).isEqualTo(AnnouncementStatus.SENT);
        assertThat(saved.getAudienceSpec().isWholeSection()).isTrue();
        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(event.capture());
        AnnouncementUpdatedEvent published = (AnnouncementUpdatedEvent) event.getValue();
        assertThat(published.announcementId()).isEqualTo(1L);
        assertThat(published.sectionName()).isEqualTo("Matematica");
        assertThat(published.studentPersonIds()).containsExactlyInAnyOrder(101L, 102L);
    }
}
