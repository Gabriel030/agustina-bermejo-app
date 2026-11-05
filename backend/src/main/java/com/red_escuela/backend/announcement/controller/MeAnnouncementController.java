package com.red_escuela.backend.announcement.controller;

import com.red_escuela.backend.announcement.dto.MyAnnouncementResponse;
import com.red_escuela.backend.announcement.usecase.announcement.ListMyAnnouncementsUseCase;
import com.red_escuela.backend.announcement.usecase.announcement.MarkAnnouncementReadUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class MeAnnouncementController {

    private final ListMyAnnouncementsUseCase listMyAnnouncements;
    private final MarkAnnouncementReadUseCase markAnnouncementRead;

    public MeAnnouncementController(ListMyAnnouncementsUseCase listMyAnnouncements,
                                    MarkAnnouncementReadUseCase markAnnouncementRead) {
        this.listMyAnnouncements = listMyAnnouncements;
        this.markAnnouncementRead = markAnnouncementRead;
    }

    @GetMapping("/me/announcements")
    public List<MyAnnouncementResponse> mine() {
        return listMyAnnouncements.listForCurrentStudent().stream().map(MyAnnouncementResponse::from).toList();
    }

    @PostMapping("/me/announcements/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@PathVariable Long id) {
        markAnnouncementRead.markRead(id);
    }
}
