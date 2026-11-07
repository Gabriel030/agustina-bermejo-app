package com.red_escuela.backend.announcement.controller;

import com.red_escuela.backend.announcement.dto.MyAnnouncementResponse;
import com.red_escuela.backend.announcement.entity.Announcement;
import com.red_escuela.backend.announcement.usecase.broadcast.ListMyAnnouncementReadTimesUseCase;
import com.red_escuela.backend.announcement.usecase.broadcast.ListMyAnnouncementsUseCase;
import com.red_escuela.backend.announcement.usecase.broadcast.MarkAnnouncementReadUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
public class MeAnnouncementController {

    private final ListMyAnnouncementsUseCase listMyAnnouncements;
    private final MarkAnnouncementReadUseCase markAnnouncementRead;
    private final ListMyAnnouncementReadTimesUseCase listReadTimes;

    public MeAnnouncementController(ListMyAnnouncementsUseCase listMyAnnouncements,
                                    MarkAnnouncementReadUseCase markAnnouncementRead,
                                    ListMyAnnouncementReadTimesUseCase listReadTimes) {
        this.listMyAnnouncements = listMyAnnouncements;
        this.markAnnouncementRead = markAnnouncementRead;
        this.listReadTimes = listReadTimes;
    }

    @GetMapping("/me/announcements")
    public List<MyAnnouncementResponse> mine() {
        List<Announcement> announcements = listMyAnnouncements.listForCurrentStudent();
        Map<Long, LocalDateTime> readTimes = listReadTimes.readTimesOf(announcements.stream().map(Announcement::getId).toList());
        return announcements.stream()
                .map(announcement -> MyAnnouncementResponse.from(announcement, readTimes.get(announcement.getId())))
                .toList();
    }

    @PostMapping("/me/announcements/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@PathVariable Long id) {
        markAnnouncementRead.markRead(id);
    }
}
