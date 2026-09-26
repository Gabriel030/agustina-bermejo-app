package com.red_escuela.backend.announcement.usecase.announcement;

import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.repository.CourseSubjectSectionRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionRepository;
import com.red_escuela.backend.announcement.AnnouncementPublishedEvent;
import com.red_escuela.backend.announcement.entity.Announcement;
import com.red_escuela.backend.announcement.entity.SectionType;
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

@UseCase
public class CreateAnnouncementService implements CreateAnnouncementUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreateAnnouncementService.class);
    public static final String WHOLE_SECTION_LABEL = "Toda la comisión";

    private final AnnouncementRepository announcementRepository;
    private final SubjectSectionRepository subjectSectionRepository;
    private final CourseSubjectSectionRepository courseSubjectSectionRepository;
    private final SectionMembership sectionMembership;
    private final CurrentInstructorService currentInstructorService;
    private final ApplicationEventPublisher eventPublisher;

    public CreateAnnouncementService(AnnouncementRepository announcementRepository,
                                     SubjectSectionRepository subjectSectionRepository,
                                     CourseSubjectSectionRepository courseSubjectSectionRepository,
                                     SectionMembership sectionMembership,
                                     CurrentInstructorService currentInstructorService,
                                     ApplicationEventPublisher eventPublisher) {
        this.announcementRepository = announcementRepository;
        this.subjectSectionRepository = subjectSectionRepository;
        this.courseSubjectSectionRepository = courseSubjectSectionRepository;
        this.sectionMembership = sectionMembership;
        this.currentInstructorService = currentInstructorService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public Announcement create(CreateAnnouncementCommand command) {
        Instructor instructor = currentInstructorService.resolveCurrentInstructor();
        sectionMembership.requireInstructorOf(command.sectionType(), command.sectionId(), instructor.getId());

        Announcement announcement = Announcement.builder()
                .title(command.title())
                .body(command.body())
                .category(command.category())
                .urgent(command.urgent())
                .expiresAt(command.expiresAt())
                .audienceLabel(WHOLE_SECTION_LABEL)
                .createdBy(instructor.getPerson())
                .createdAt(LocalDateTime.now())
                .build();

        String sectionName;
        if (command.sectionType() == SectionType.PROGRAM) {
            SubjectSection section = subjectSectionRepository.findById(command.sectionId()).orElseThrow();
            announcement.setSubjectSection(section);
            sectionName = AnnouncementSectionNames.of(section);
        } else {
            CourseSubjectSection section = courseSubjectSectionRepository.findById(command.sectionId()).orElseThrow();
            announcement.setCourseSubjectSection(section);
            sectionName = AnnouncementSectionNames.of(section);
        }

        Announcement saved = announcementRepository.save(announcement);
        List<Student> students = sectionMembership.studentsOf(command.sectionType(), command.sectionId());

        log.info("Aviso publicado: id={}, comision={}/{}, destinatarios={}",
                saved.getId(), command.sectionType(), command.sectionId(), students.size());

        eventPublisher.publishEvent(new AnnouncementPublishedEvent(
                saved, sectionName, students.stream().map(Student::getPerson).toList()));
        return saved;
    }
}
