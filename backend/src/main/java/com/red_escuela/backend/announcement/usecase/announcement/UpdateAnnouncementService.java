package com.red_escuela.backend.announcement.usecase.announcement;

import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.announcement.AnnouncementUpdatedEvent;
import com.red_escuela.backend.announcement.entity.Announcement;
import com.red_escuela.backend.announcement.exception.AnnouncementNotFoundException;
import com.red_escuela.backend.announcement.repository.AnnouncementRepository;
import com.red_escuela.backend.announcement.service.AnnouncementSectionNames;
import com.red_escuela.backend.announcement.service.SectionMembership;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.service.CurrentInstructorService;
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
    private final SectionMembership sectionMembership;
    private final CurrentInstructorService currentInstructorService;
    private final ApplicationEventPublisher eventPublisher;

    public UpdateAnnouncementService(AnnouncementRepository announcementRepository,
                                     SectionMembership sectionMembership,
                                     CurrentInstructorService currentInstructorService,
                                     ApplicationEventPublisher eventPublisher) {
        this.announcementRepository = announcementRepository;
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

        Optional.ofNullable(command.title()).ifPresent(announcement::setTitle);
        Optional.ofNullable(command.body()).ifPresent(announcement::setBody);
        Optional.ofNullable(command.category()).ifPresent(announcement::setCategory);
        announcement.setUrgent(command.urgent());
        announcement.setExpiresAt(command.expiresAt());
        announcement.setUpdatedAt(LocalDateTime.now());

        Announcement saved = announcementRepository.save(announcement);
        List<Student> students = sectionMembership.studentsOf(saved.getSectionType(), saved.getSectionId());
        String sectionName = saved.getSubjectSection() != null
                ? AnnouncementSectionNames.of(saved.getSubjectSection())
                : AnnouncementSectionNames.of(saved.getCourseSubjectSection());

        log.info("Aviso actualizado: id={}, destinatarios renotificados={}", saved.getId(), students.size());

        eventPublisher.publishEvent(new AnnouncementUpdatedEvent(
                saved, sectionName, students.stream().map(Student::getPerson).toList()));
        return saved;
    }
}
