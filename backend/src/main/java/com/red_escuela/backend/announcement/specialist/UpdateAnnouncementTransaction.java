package com.red_escuela.backend.announcement.specialist;

import com.red_escuela.backend.announcement.entity.Announcement;
import com.red_escuela.backend.announcement.entity.AnnouncementCategory;
import com.red_escuela.backend.announcement.entity.AudienceSpec;
import com.red_escuela.backend.announcement.exception.AnnouncementAlreadySentException;
import com.red_escuela.backend.announcement.exception.AnnouncementNotFoundException;
import com.red_escuela.backend.announcement.exception.CannotChangeAudienceOfSentAnnouncementException;
import com.red_escuela.backend.announcement.exception.ScheduledDateInThePastException;
import com.red_escuela.backend.announcement.repository.AnnouncementRepository;
import com.red_escuela.backend.announcement.specialist.audience.AudienceLabels;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class UpdateAnnouncementTransaction {

    private static final Logger log = LoggerFactory.getLogger(UpdateAnnouncementTransaction.class);

    private final AnnouncementRepository announcementRepository;
    private final SectionMembershipChecker sectionMembership;
    private final AnnouncementRenotifier announcementRenotifier;

    public UpdateAnnouncementTransaction(AnnouncementRepository announcementRepository,
                                         SectionMembershipChecker sectionMembership,
                                         AnnouncementRenotifier announcementRenotifier) {
        this.announcementRepository = announcementRepository;
        this.sectionMembership = sectionMembership;
        this.announcementRenotifier = announcementRenotifier;
    }

    @Transactional
    public Announcement update(Long announcementId, String title, String body, AnnouncementCategory category,
                               boolean urgent, LocalDate expiresAt, LocalDateTime scheduledFor,
                               AudienceSpec audienceSpec, boolean notifyRecipients, LocalDateTime now) {
        Announcement announcement = announcementRepository.findById(announcementId)
                .orElseThrow(() -> new AnnouncementNotFoundException(announcementId));

        sectionMembership.requireCurrentInstructorOf(announcement.getSectionType(), announcement.getSectionId());

        boolean scheduled = announcement.isScheduled();
        if (!scheduled && audienceSpec != null) {
            throw new CannotChangeAudienceOfSentAnnouncementException(announcement.getId());
        }
        if (!scheduled && scheduledFor != null) {
            throw new AnnouncementAlreadySentException(announcement.getId());
        }
        if (scheduled && scheduledFor != null && !scheduledFor.isAfter(now)) {
            throw new ScheduledDateInThePastException(scheduledFor);
        }

        Optional.ofNullable(title).ifPresent(announcement::setTitle);
        Optional.ofNullable(body).ifPresent(announcement::setBody);
        Optional.ofNullable(category).ifPresent(announcement::setCategory);
        announcement.setUrgent(urgent);
        announcement.setExpiresAt(expiresAt);
        announcement.setUpdatedAt(now);
        if (scheduled) {
            Optional.ofNullable(scheduledFor).ifPresent(announcement::setScheduledFor);
            Optional.ofNullable(audienceSpec).ifPresent(spec -> {
                announcement.setAudienceSpec(spec);
                announcement.setAudienceLabel(AudienceLabels.resolvedLabel(spec));
            });
        }

        Announcement saved = announcementRepository.save(announcement);
        if (scheduled || !notifyRecipients) {
            log.info("Aviso actualizado sin notificar: id={}, sale={}, segmento={}",
                    saved.getId(), saved.getScheduledFor(), saved.getAudienceSpec().getKey());
            return saved;
        }

        announcementRenotifier.renotifyFrozenRecipients(saved);
        return saved;
    }
}
