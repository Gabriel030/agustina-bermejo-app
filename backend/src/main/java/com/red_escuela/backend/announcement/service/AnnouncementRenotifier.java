package com.red_escuela.backend.announcement.service;

import com.red_escuela.backend.announcement.entity.Announcement;
import com.red_escuela.backend.announcement.entity.AnnouncementRecipient;
import com.red_escuela.backend.announcement.repository.AnnouncementRecipientRepository;
import com.red_escuela.backend.identity.entity.Student;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AnnouncementRenotifier {

    private static final Logger log = LoggerFactory.getLogger(AnnouncementRenotifier.class);

    private final AnnouncementRecipientRepository recipientRepository;
    private final ApplicationEventPublisher eventPublisher;

    public AnnouncementRenotifier(AnnouncementRecipientRepository recipientRepository,
                                  ApplicationEventPublisher eventPublisher) {
        this.recipientRepository = recipientRepository;
        this.eventPublisher = eventPublisher;
    }

    public void renotifyFrozenRecipients(Announcement announcement) {
        List<Student> students = recipientRepository.findByAnnouncementId(announcement.getId()).stream()
                .map(AnnouncementRecipient::getStudent)
                .toList();
        String sectionName = announcement.getSubjectSection() != null
                ? AnnouncementSectionNames.of(announcement.getSubjectSection())
                : AnnouncementSectionNames.of(announcement.getCourseSubjectSection());

        log.info("Aviso actualizado: id={}, se renotifica a los mismos {} destinatarios congelados",
                announcement.getId(), students.size());

        eventPublisher.publishEvent(AnnouncementEventFactory.updatedEventOf(announcement, sectionName, students));
    }
}
