package com.red_escuela.backend.announcement.service;

import com.red_escuela.backend.announcement.entity.Announcement;
import com.red_escuela.backend.announcement.events.AnnouncementPublishedEvent;
import com.red_escuela.backend.announcement.events.AnnouncementUpdatedEvent;
import com.red_escuela.backend.identity.entity.Student;

import java.util.List;

public final class AnnouncementEventFactory {

    private AnnouncementEventFactory() {
    }

    public static AnnouncementPublishedEvent publishedEventOf(Announcement announcement, String sectionName, List<Student> students) {
        return AnnouncementPublishedEvent.publishedFor(announcement.getId(), sectionName, personIdsOf(students));
    }

    public static AnnouncementUpdatedEvent updatedEventOf(Announcement announcement, String sectionName, List<Student> students) {
        return AnnouncementUpdatedEvent.updatedFor(announcement.getId(), sectionName, personIdsOf(students));
    }

    private static List<Long> personIdsOf(List<Student> students) {
        return students.stream().map(student -> student.getPerson().getId()).toList();
    }
}
