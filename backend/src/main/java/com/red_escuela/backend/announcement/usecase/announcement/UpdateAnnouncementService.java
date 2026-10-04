package com.red_escuela.backend.announcement.usecase.announcement;

import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.announcement.AnnouncementUpdatedEvent;
import com.red_escuela.backend.announcement.entity.Announcement;
import com.red_escuela.backend.announcement.entity.AnnouncementRecipient;
import com.red_escuela.backend.announcement.exception.AnnouncementAlreadySentException;
import com.red_escuela.backend.announcement.exception.AnnouncementNotFoundException;
import com.red_escuela.backend.announcement.exception.CannotChangeAudienceOfSentAnnouncementException;
import com.red_escuela.backend.announcement.exception.ScheduledDateInThePastException;
import com.red_escuela.backend.announcement.service.audience.AudienceLabels;
import com.red_escuela.backend.announcement.repository.AnnouncementRecipientRepository;
import com.red_escuela.backend.announcement.repository.AnnouncementRepository;
import com.red_escuela.backend.announcement.service.AnnouncementSectionNames;
import com.red_escuela.backend.announcement.service.SectionMembership;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.academicstructure.service.CurrentInstructorService;
import com.red_escuela.backend.shared.UseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@UseCase
public class UpdateAnnouncementService implements UpdateAnnouncementUseCase {

    private static final Logger log = LoggerFactory.getLogger(UpdateAnnouncementService.class);

    private final AnnouncementRepository announcementRepository;
    private final AnnouncementRecipientRepository recipientRepository;
    private final SectionMembership sectionMembership;
    private final CurrentInstructorService currentInstructorService;
    private final ApplicationEventPublisher eventPublisher;

    public UpdateAnnouncementService(AnnouncementRepository announcementRepository,
                                     AnnouncementRecipientRepository recipientRepository,
                                     SectionMembership sectionMembership,
                                     CurrentInstructorService currentInstructorService,
                                     ApplicationEventPublisher eventPublisher) {
        this.announcementRepository = announcementRepository;
        this.recipientRepository = recipientRepository;
        this.sectionMembership = sectionMembership;
        this.currentInstructorService = currentInstructorService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public Announcement update(UpdateAnnouncementCommand command) {
        Announcement announcement = announcementRepository.findById(command.announcementId())
                .orElseThrow(() -> new AnnouncementNotFoundException(command.announcementId()));

        Instructor instructor = currentInstructorService.resolveCurrentInstructor();
        sectionMembership.requireInstructorOf(announcement.getSectionType(), announcement.getSectionId(), instructor.getId());

        LocalDateTime now = LocalDateTime.now();
        boolean scheduled = announcement.isScheduled();
        if (!scheduled && command.audienceSpec() != null) {
            throw new CannotChangeAudienceOfSentAnnouncementException(announcement.getId());
        }
        if (!scheduled && command.scheduledFor() != null) {
            throw new AnnouncementAlreadySentException(announcement.getId());
        }
        if (scheduled && command.scheduledFor() != null && !command.scheduledFor().isAfter(now)) {
            throw new ScheduledDateInThePastException(command.scheduledFor());
        }

        Optional.ofNullable(command.title()).ifPresent(announcement::setTitle);
        Optional.ofNullable(command.body()).ifPresent(announcement::setBody);
        Optional.ofNullable(command.category()).ifPresent(announcement::setCategory);
        announcement.setUrgent(command.urgent());
        announcement.setExpiresAt(command.expiresAt());
        announcement.setUpdatedAt(now);
        if (scheduled) {
            Optional.ofNullable(command.scheduledFor()).ifPresent(announcement::setScheduledFor);
            Optional.ofNullable(command.audienceSpec()).ifPresent(spec -> {
                announcement.setAudienceSpec(spec);
                announcement.setAudienceLabel(AudienceLabels.resolvedLabel(spec));
            });
        }

        Announcement saved = announcementRepository.save(announcement);
        if (scheduled) {
            log.info("Aviso programado actualizado: id={}, sale={}, segmento={} — no se notifica a nadie todavía",
                    saved.getId(), saved.getScheduledFor(), saved.getAudienceSpec().getKey());
            return saved;
        }

        List<Student> students = recipientRepository.findByAnnouncementId(saved.getId()).stream()
                .map(AnnouncementRecipient::getStudent)
                .toList();
        String sectionName = saved.getSubjectSection() != null
                ? AnnouncementSectionNames.of(saved.getSubjectSection())
                : AnnouncementSectionNames.of(saved.getCourseSubjectSection());

        log.info("Aviso actualizado: id={}, se renotifica a los mismos {} destinatarios congelados", saved.getId(), students.size());

        eventPublisher.publishEvent(new AnnouncementUpdatedEvent(
                saved, sectionName, students.stream().map(Student::getPerson).toList()));
        return saved;
    }
}
