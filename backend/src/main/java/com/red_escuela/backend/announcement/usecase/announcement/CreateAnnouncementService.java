package com.red_escuela.backend.announcement.usecase.announcement;

import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.repository.CourseSubjectSectionRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionRepository;
import com.red_escuela.backend.announcement.entity.Announcement;
import com.red_escuela.backend.announcement.entity.AnnouncementStatus;
import com.red_escuela.backend.announcement.entity.AudienceSpec;
import com.red_escuela.backend.announcement.entity.SectionType;
import com.red_escuela.backend.announcement.exception.ScheduledDateInThePastException;
import com.red_escuela.backend.announcement.repository.AnnouncementRepository;
import com.red_escuela.backend.announcement.service.AnnouncementDispatcher;
import com.red_escuela.backend.announcement.service.SectionMembership;
import com.red_escuela.backend.announcement.service.audience.AudienceLabels;
import com.red_escuela.backend.academicstructure.service.CurrentInstructorService;
import com.red_escuela.backend.shared.UseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@UseCase
public class CreateAnnouncementService implements CreateAnnouncementUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreateAnnouncementService.class);

    private final AnnouncementRepository announcementRepository;
    private final SubjectSectionRepository subjectSectionRepository;
    private final CourseSubjectSectionRepository courseSubjectSectionRepository;
    private final SectionMembership sectionMembership;
    private final AnnouncementDispatcher dispatcher;
    private final CurrentInstructorService currentInstructorService;

    public CreateAnnouncementService(AnnouncementRepository announcementRepository,
                                     SubjectSectionRepository subjectSectionRepository,
                                     CourseSubjectSectionRepository courseSubjectSectionRepository,
                                     SectionMembership sectionMembership,
                                     AnnouncementDispatcher dispatcher,
                                     CurrentInstructorService currentInstructorService) {
        this.announcementRepository = announcementRepository;
        this.subjectSectionRepository = subjectSectionRepository;
        this.courseSubjectSectionRepository = courseSubjectSectionRepository;
        this.sectionMembership = sectionMembership;
        this.dispatcher = dispatcher;
        this.currentInstructorService = currentInstructorService;
    }

    @Override
    @Transactional
    public Announcement create(CreateAnnouncementCommand command) {
        Instructor instructor = currentInstructorService.resolveCurrentInstructor();
        sectionMembership.requireInstructorOf(command.sectionType(), command.sectionId(), instructor.getId());

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
                .createdBy(instructor.getPerson())
                .createdAt(now)
                .status(scheduled ? AnnouncementStatus.SCHEDULED : AnnouncementStatus.SENT)
                .scheduledFor(command.scheduledFor())
                .sentAt(scheduled ? null : now)
                .build();
        attachSection(announcement, command.sectionType(), command.sectionId());

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

    private void attachSection(Announcement announcement, SectionType sectionType, Long sectionId) {
        if (sectionType == SectionType.PROGRAM) {
            SubjectSection target = subjectSectionRepository.findById(sectionId).orElseThrow();
            announcement.setSubjectSection(target);
        } else {
            CourseSubjectSection target = courseSubjectSectionRepository.findById(sectionId).orElseThrow();
            announcement.setCourseSubjectSection(target);
        }
    }
}
