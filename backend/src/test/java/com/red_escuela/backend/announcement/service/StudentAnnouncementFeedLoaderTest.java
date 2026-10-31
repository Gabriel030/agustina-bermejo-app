package com.red_escuela.backend.announcement.service;

import com.red_escuela.backend.announcement.entity.Announcement;
import com.red_escuela.backend.announcement.repository.AnnouncementRepository;
import com.red_escuela.backend.identity.api.StudentFinder;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class StudentAnnouncementFeedLoaderTest {

    private final AnnouncementRepository announcementRepository = mock(AnnouncementRepository.class);
    private final StudentFinder studentFinder = mock(StudentFinder.class);
    private final StudentAnnouncementFeedLoader loader = new StudentAnnouncementFeedLoader(announcementRepository, studentFinder);

    @Test
    void loadsTheFeedOfTheStudentLinkedToThePerson() {
        Announcement announcement = new Announcement();
        when(studentFinder.findIdByPersonId(4L)).thenReturn(Optional.of(9L));
        when(announcementRepository.findFeedForStudent(eq(9L), any(LocalDate.class))).thenReturn(List.of(announcement));

        assertThat(loader.loadFeedOfPerson(4L)).containsExactly(announcement);
    }

    @Test
    void returnsAnEmptyFeedWhenThePersonIsNotAStudent() {
        when(studentFinder.findIdByPersonId(4L)).thenReturn(Optional.empty());

        assertThat(loader.loadFeedOfPerson(4L)).isEmpty();

        verifyNoInteractions(announcementRepository);
    }
}
