package com.red_escuela.backend.announcement.service;

import com.red_escuela.backend.announcement.AnnouncementPublishedEvent;
import com.red_escuela.backend.announcement.entity.Announcement;
import com.red_escuela.backend.announcement.entity.AnnouncementRecipient;
import com.red_escuela.backend.announcement.entity.AudienceSpec;
import com.red_escuela.backend.announcement.exception.EmptyAudienceException;
import com.red_escuela.backend.announcement.repository.AnnouncementRecipientRepository;
import com.red_escuela.backend.announcement.repository.AnnouncementRepository;
import com.red_escuela.backend.announcement.service.audience.AudienceService;
import com.red_escuela.backend.announcement.service.audience.SectionSnapshot;
import com.red_escuela.backend.announcement.service.audience.StudentSnapshot;
import com.red_escuela.backend.identity.entity.Student;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AnnouncementDispatcher {

    private static final Logger log = LoggerFactory.getLogger(AnnouncementDispatcher.class);

    private final AnnouncementRepository announcementRepository;
    private final AnnouncementRecipientRepository recipientRepository;
    private final AudienceService audienceService;
    private final ApplicationEventPublisher eventPublisher;

    public AnnouncementDispatcher(AnnouncementRepository announcementRepository,
                                  AnnouncementRecipientRepository recipientRepository,
                                  AudienceService audienceService,
                                  ApplicationEventPublisher eventPublisher) {
        this.announcementRepository = announcementRepository;
        this.recipientRepository = recipientRepository;
        this.audienceService = audienceService;
        this.eventPublisher = eventPublisher;
    }

    public int publishNow(Announcement announcement) {
        List<StudentSnapshot> matched = resolveAudience(announcement);
        if (matched.isEmpty()) {
            throw new EmptyAudienceException();
        }
        return freezeAndNotify(announcement, matched);
    }

    public int dispatchScheduled(Announcement announcement, LocalDateTime when) {
        List<StudentSnapshot> matched = resolveAudience(announcement);
        announcement.markAsSent(when);
        int sent = freezeAndNotify(announcement, matched);
        if (sent == 0) {
            log.warn("Aviso programado enviado a cero destinatarios: id={}, comision={}/{}, segmento={}",
                    announcement.getId(), announcement.getSectionType(), announcement.getSectionId(),
                    announcement.getAudienceSpec().getKey());
        }
        return sent;
    }

    private List<StudentSnapshot> resolveAudience(Announcement announcement) {
        SectionSnapshot section = audienceService.snapshotOf(announcement.getSectionType(), announcement.getSectionId());
        AudienceSpec spec = announcement.getAudienceSpec() == null
                ? AudienceSpec.wholeSection() : announcement.getAudienceSpec();
        return audienceService.resolve(section, spec);
    }

    private int freezeAndNotify(Announcement announcement, List<StudentSnapshot> matched) {
        List<Student> students = matched.stream().map(StudentSnapshot::student).toList();
        students.forEach(student -> recipientRepository.save(
                AnnouncementRecipient.builder().announcement(announcement).student(student).build()));

        Announcement saved = announcementRepository.save(announcement);

        eventPublisher.publishEvent(new AnnouncementPublishedEvent(
                saved, sectionNameOf(saved), students.stream().map(Student::getPerson).toList()));
        return students.size();
    }

    private String sectionNameOf(Announcement announcement) {
        return announcement.getSubjectSection() != null
                ? AnnouncementSectionNames.of(announcement.getSubjectSection())
                : AnnouncementSectionNames.of(announcement.getCourseSubjectSection());
    }
}
