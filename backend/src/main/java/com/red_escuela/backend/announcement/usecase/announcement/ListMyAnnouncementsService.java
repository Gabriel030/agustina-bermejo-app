package com.red_escuela.backend.announcement.usecase.announcement;

import com.red_escuela.backend.announcement.entity.Announcement;
import com.red_escuela.backend.announcement.service.AnnouncementViewer;
import com.red_escuela.backend.announcement.service.StudentAnnouncementFeedLoader;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ListMyAnnouncementsService implements ListMyAnnouncementsUseCase {

    private final StudentAnnouncementFeedLoader feedLoader;
    private final AnnouncementViewer announcementViewer;

    public ListMyAnnouncementsService(StudentAnnouncementFeedLoader feedLoader,
                                      AnnouncementViewer announcementViewer) {
        this.feedLoader = feedLoader;
        this.announcementViewer = announcementViewer;
    }

    @Override
    public List<Announcement> listForCurrentStudent() {
        return feedLoader.loadFeedOfPerson(announcementViewer.currentPersonId());
    }
}
