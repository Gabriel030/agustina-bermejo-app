package com.red_escuela.backend.announcement.usecase.announcement;

import com.red_escuela.backend.announcement.entity.Announcement;
import com.red_escuela.backend.announcement.exception.AnnouncementAlreadySentException;
import com.red_escuela.backend.announcement.exception.AnnouncementNotFoundException;
import com.red_escuela.backend.announcement.exception.CannotChangeAudienceOfSentAnnouncementException;
import com.red_escuela.backend.announcement.exception.ScheduledDateInThePastException;
import com.red_escuela.backend.announcement.service.audience.AudienceLabels;
import com.red_escuela.backend.announcement.repository.AnnouncementRepository;
import com.red_escuela.backend.announcement.service.AnnouncementRenotifier;
import com.red_escuela.backend.announcement.service.SectionMembership;
import com.red_escuela.backend.shared.UseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@UseCase
public class UpdateAnnouncementService implements UpdateAnnouncementUseCase {

    private static final Logger log = LoggerFactory.getLogger(UpdateAnnouncementService.class);

    private final AnnouncementRepository announcementRepository;
    private final SectionMembership sectionMembership;
    private final AnnouncementRenotifier announcementRenotifier;

    public UpdateAnnouncementService(AnnouncementRepository announcementRepository,
                                     SectionMembership sectionMembership,
                                     AnnouncementRenotifier announcementRenotifier) {
        this.announcementRepository = announcementRepository;
        this.sectionMembership = sectionMembership;
        this.announcementRenotifier = announcementRenotifier;
    }

    @Override
    @Transactional
    public Announcement update(UpdateAnnouncementCommand command) {
        Announcement announcement = announcementRepository.findById(command.announcementId())
                .orElseThrow(() -> new AnnouncementNotFoundException(command.announcementId()));

        sectionMembership.requireCurrentInstructorOf(announcement.getSectionType(), announcement.getSectionId());

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
        if (scheduled || !command.notifyRecipients()) {
            log.info("Aviso actualizado sin notificar: id={}, sale={}, segmento={}",
                    saved.getId(), saved.getScheduledFor(), saved.getAudienceSpec().getKey());
            return saved;
        }

        announcementRenotifier.renotifyFrozenRecipients(saved);
        return saved;
    }
}
