package com.red_escuela.backend.announcement.specialist;

import com.red_escuela.backend.announcement.entity.Announcement;
import com.red_escuela.backend.announcement.entity.AnnouncementCategory;
import com.red_escuela.backend.announcement.entity.AnnouncementStatus;
import com.red_escuela.backend.announcement.entity.AudienceSpec;
import com.red_escuela.backend.announcement.entity.SectionType;
import com.red_escuela.backend.announcement.exception.ScheduledDateInThePastException;
import com.red_escuela.backend.announcement.repository.AnnouncementRepository;
import com.red_escuela.backend.announcement.specialist.audience.AudienceLabels;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class CreateAnnouncementTransaction {

    private static final Logger log = LoggerFactory.getLogger(CreateAnnouncementTransaction.class);

    private final AnnouncementRepository announcementRepository;
    private final SectionMembershipChecker sectionMembership;
    private final AnnouncementSectionAssigner sectionAssigner;
    private final AnnouncementDispatcher dispatcher;
    private final CurrentAnnouncementAuthorFinder currentAuthor;

    public CreateAnnouncementTransaction(AnnouncementRepository announcementRepository,
                                         SectionMembershipChecker sectionMembership,
                                         AnnouncementSectionAssigner sectionAssigner,
                                         AnnouncementDispatcher dispatcher,
                                         CurrentAnnouncementAuthorFinder currentAuthor) {
        this.announcementRepository = announcementRepository;
        this.sectionMembership = sectionMembership;
        this.sectionAssigner = sectionAssigner;
        this.dispatcher = dispatcher;
        this.currentAuthor = currentAuthor;
    }

    @Transactional
    public Announcement create(SectionType sectionType, Long sectionId, String title, String body,
                               AnnouncementCategory category, boolean urgent, LocalDate expiresAt,
                               LocalDateTime scheduledFor, AudienceSpec audienceSpec, LocalDateTime now) {
        sectionMembership.requireCurrentInstructorOf(sectionType, sectionId);

        boolean scheduled = scheduledFor != null;
        if (scheduled && !scheduledFor.isAfter(now)) {
            throw new ScheduledDateInThePastException(scheduledFor);
        }

        AudienceSpec spec = audienceSpec == null ? AudienceSpec.wholeSection() : audienceSpec;
        Announcement announcement = Announcement.builder()
                .title(title)
                .body(body)
                .category(category)
                .urgent(urgent)
                .expiresAt(expiresAt)
                .audienceSpec(spec)
                .audienceLabel(AudienceLabels.resolvedLabel(spec))
                .createdBy(currentAuthor.currentAuthor())
                .createdAt(now)
                .status(scheduled ? AnnouncementStatus.SCHEDULED : AnnouncementStatus.SENT)
                .scheduledFor(scheduledFor)
                .sentAt(scheduled ? null : now)
                .build();
        sectionAssigner.assignSection(announcement, sectionType, sectionId);

        if (scheduled) {
            Announcement saved = announcementRepository.save(announcement);
            log.info("Aviso programado: id={}, comision={}/{}, segmento={}, sale={}",
                    saved.getId(), sectionType, sectionId, spec.getKey(), saved.getScheduledFor());
            return saved;
        }

        Announcement saved = announcementRepository.save(announcement);
        int recipients = dispatcher.publishNow(saved);
        log.info("Aviso publicado: id={}, comision={}/{}, segmento={}, instancias={}, cuantificador={}, destinatarios={}",
                saved.getId(), sectionType, sectionId, spec.getKey(), spec.getInstances(),
                spec.getQuantifier(), recipients);
        return saved;
    }
}
