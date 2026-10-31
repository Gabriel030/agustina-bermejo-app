package com.red_escuela.backend.announcement.service;

import com.red_escuela.backend.announcement.entity.Announcement;
import com.red_escuela.backend.announcement.repository.AnnouncementRepository;
import com.red_escuela.backend.identity.api.StudentFinder;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
public class StudentAnnouncementFeedLoader {

    private final AnnouncementRepository announcementRepository;
    private final StudentFinder studentFinder;
    private final Clock clock;

    public StudentAnnouncementFeedLoader(AnnouncementRepository announcementRepository, StudentFinder studentFinder, Clock clock) {
        this.announcementRepository = announcementRepository;
        this.studentFinder = studentFinder;
        this.clock = clock;
    }

    public List<Announcement> loadFeedOfPerson(Long personId) {
        return studentFinder.findIdByPersonId(personId)
                .map(studentId -> announcementRepository.findFeedForStudent(studentId, LocalDate.now(clock)))
                .orElseGet(List::of);
    }
}
