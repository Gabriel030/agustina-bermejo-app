package com.red_escuela.backend.announcement.usecase.announcement;

import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.repository.CourseSubjectSectionRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionRepository;
import com.red_escuela.backend.announcement.AnnouncementPublishedEvent;
import com.red_escuela.backend.announcement.entity.Announcement;
import com.red_escuela.backend.announcement.entity.AnnouncementRecipient;
import com.red_escuela.backend.announcement.entity.AudienceSpec;
import com.red_escuela.backend.announcement.entity.SectionType;
import com.red_escuela.backend.announcement.exception.EmptyAudienceException;
import com.red_escuela.backend.announcement.repository.AnnouncementRecipientRepository;
import com.red_escuela.backend.announcement.repository.AnnouncementRepository;
import com.red_escuela.backend.announcement.service.AnnouncementSectionNames;
import com.red_escuela.backend.announcement.service.SectionMembership;
import com.red_escuela.backend.announcement.service.audience.AudienceLabels;
import com.red_escuela.backend.announcement.service.audience.AudienceService;
import com.red_escuela.backend.announcement.service.audience.SectionSnapshot;
import com.red_escuela.backend.announcement.service.audience.StudentSnapshot;
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

    private final AnnouncementRepository announcementRepository;
    private final AnnouncementRecipientRepository recipientRepository;
    private final SubjectSectionRepository subjectSectionRepository;
    private final CourseSubjectSectionRepository courseSubjectSectionRepository;
    private final SectionMembership sectionMembership;
    private final AudienceService audienceService;
    private final CurrentInstructorService currentInstructorService;
    private final ApplicationEventPublisher eventPublisher;

    public CreateAnnouncementService(AnnouncementRepository announcementRepository,
                                     AnnouncementRecipientRepository recipientRepository,
                                     SubjectSectionRepository subjectSectionRepository,
                                     CourseSubjectSectionRepository courseSubjectSectionRepository,
                                     SectionMembership sectionMembership,
                                     AudienceService audienceService,
                                     CurrentInstructorService currentInstructorService,
                                     ApplicationEventPublisher eventPublisher) {
        this.announcementRepository = announcementRepository;
        this.recipientRepository = recipientRepository;
        this.subjectSectionRepository = subjectSectionRepository;
        this.courseSubjectSectionRepository = courseSubjectSectionRepository;
        this.sectionMembership = sectionMembership;
        this.audienceService = audienceService;
        this.currentInstructorService = currentInstructorService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public Announcement create(CreateAnnouncementCommand command) {
        Instructor instructor = currentInstructorService.resolveCurrentInstructor();
        sectionMembership.requireInstructorOf(command.sectionType(), command.sectionId(), instructor.getId());

        AudienceSpec spec = command.audienceSpec() == null ? AudienceSpec.wholeSection() : command.audienceSpec();
        SectionSnapshot section = audienceService.snapshotOf(command.sectionType(), command.sectionId());
        List<StudentSnapshot> matched = audienceService.resolve(section, spec);
        if (matched.isEmpty()) {
            throw new EmptyAudienceException();
        }

        Announcement announcement = Announcement.builder()
                .title(command.title())
                .body(command.body())
                .category(command.category())
                .urgent(command.urgent())
                .expiresAt(command.expiresAt())
                .audienceSpec(spec)
                .audienceLabel(AudienceLabels.resolvedLabel(spec))
                .createdBy(instructor.getPerson())
                .createdAt(LocalDateTime.now())
                .build();

        String sectionName;
        if (command.sectionType() == SectionType.PROGRAM) {
            SubjectSection target = subjectSectionRepository.findById(command.sectionId()).orElseThrow();
            announcement.setSubjectSection(target);
            sectionName = AnnouncementSectionNames.of(target);
        } else {
            CourseSubjectSection target = courseSubjectSectionRepository.findById(command.sectionId()).orElseThrow();
            announcement.setCourseSubjectSection(target);
            sectionName = AnnouncementSectionNames.of(target);
        }

        Announcement saved = announcementRepository.save(announcement);
        List<Student> students = matched.stream().map(StudentSnapshot::student).toList();
        students.forEach(student -> recipientRepository.save(
                AnnouncementRecipient.builder().announcement(saved).student(student).build()));

        log.info("Aviso publicado: id={}, comision={}/{}, segmento={}, instancias={}, cuantificador={}, destinatarios={}/{}",
                saved.getId(), command.sectionType(), command.sectionId(), spec.getKey(), spec.getInstances(),
                spec.getQuantifier(), students.size(), section.students().size());

        eventPublisher.publishEvent(new AnnouncementPublishedEvent(
                saved, sectionName, students.stream().map(Student::getPerson).toList()));
        return saved;
    }
}
