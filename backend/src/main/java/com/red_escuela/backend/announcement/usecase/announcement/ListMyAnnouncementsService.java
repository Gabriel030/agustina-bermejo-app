package com.red_escuela.backend.announcement.usecase.announcement;

import com.red_escuela.backend.announcement.entity.Announcement;
import com.red_escuela.backend.announcement.service.StudentAnnouncementFeedLoader;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ListMyAnnouncementsService implements ListMyAnnouncementsUseCase {

    private final StudentAnnouncementFeedLoader feedLoader;
    private final CurrentPersonService currentPersonService;

    public ListMyAnnouncementsService(StudentAnnouncementFeedLoader feedLoader,
                                      CurrentPersonService currentPersonService) {
        this.feedLoader = feedLoader;
        this.currentPersonService = currentPersonService;
    }

    @Override
    public List<Announcement> listForCurrentStudent() {
        Person person = currentPersonService.resolveCurrentPerson();
        return feedLoader.loadFeedOfPerson(person.getId());
    }
}
