package com.red_escuela.backend.announcement.usecase.announcement;

import com.red_escuela.backend.announcement.entity.Announcement;
import com.red_escuela.backend.announcement.entity.AnnouncementStatus;
import com.red_escuela.backend.announcement.entity.AudienceSpec;
import com.red_escuela.backend.announcement.exception.ScheduledDateInThePastException;
import com.red_escuela.backend.announcement.repository.AnnouncementRepository;
import com.red_escuela.backend.announcement.service.AnnouncementDispatcher;
import com.red_escuela.backend.announcement.service.AnnouncementSectionAssigner;
import com.red_escuela.backend.announcement.service.CurrentAnnouncementAuthor;
import com.red_escuela.backend.announcement.service.SectionMembership;
import com.red_escuela.backend.announcement.service.audience.AudienceLabels;
import com.red_escuela.backend.shared.UseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@UseCase
public class CreateAnnouncementService implements CreateAnnouncementUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreateAnnouncementService.class);

    private final AnnouncementRepository announcementRepository;
    private final SectionMembership sectionMembership;
    private final AnnouncementSectionAssigner sectionAssigner;
    private final AnnouncementDispatcher dispatcher;
    private final CurrentAnnouncementAuthor currentAuthor;

    public CreateAnnouncementService(AnnouncementRepository announcementRepository,
                                     SectionMembership sectionMembership,
                                     AnnouncementSectionAssigner sectionAssigner,
                                     AnnouncementDispatcher dispatcher,
                                     CurrentAnnouncementAuthor currentAuthor) {
        this.announcementRepository = announcementRepository;
        this.sectionMembership = sectionMembership;
        this.sectionAssigner = sectionAssigner;
        this.dispatcher = dispatcher;
        this.currentAuthor = currentAuthor;
    }

    @Override
    @Transactional
    public Announcement create(CreateAnnouncementCommand command) {
        sectionMembership.requireCurrentInstructorOf(command.sectionType(), command.sectionId());

        LocalDateTime now = LocalDateTime.now();
        boolean scheduled = command.scheduledFor() != null;
        if (scheduled && !command.scheduledFor().isAfter(now)) {
            throw new ScheduledDateInThePastException(command.scheduledFor());
        }

        AudienceSpec spec = command.audienceSpec() == null ? AudienceSpec.wholeSection() : command.audienceSpec();
        Announcement announcement = Announcement.builder()
                .title(command.title())
                .body(command.body())
                .category(command.category())
                .urgent(command.urgent())
                .expiresAt(command.expiresAt())
                .audienceSpec(spec)
                .audienceLabel(AudienceLabels.resolvedLabel(spec))
                .createdBy(currentAuthor.currentAuthor())
                .createdAt(now)
                .status(scheduled ? AnnouncementStatus.SCHEDULED : AnnouncementStatus.SENT)
                .scheduledFor(command.scheduledFor())
                .sentAt(scheduled ? null : now)
                .build();
        sectionAssigner.assignSection(announcement, command.sectionType(), command.sectionId());

        if (scheduled) {
            Announcement saved = announcementRepository.save(announcement);
            log.info("Aviso programado: id={}, comision={}/{}, segmento={}, sale={}",
                    saved.getId(), command.sectionType(), command.sectionId(), spec.getKey(), saved.getScheduledFor());
            return saved;
        }

        Announcement saved = announcementRepository.save(announcement);
        int recipients = dispatcher.publishNow(saved);
        log.info("Aviso publicado: id={}, comision={}/{}, segmento={}, instancias={}, cuantificador={}, destinatarios={}",
                saved.getId(), command.sectionType(), command.sectionId(), spec.getKey(), spec.getInstances(),
                spec.getQuantifier(), recipients);
        return saved;
    }
}
