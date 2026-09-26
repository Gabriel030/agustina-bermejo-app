package com.red_escuela.backend.announcement.controller;

import com.red_escuela.backend.announcement.dto.MyAnnouncementResponse;
import com.red_escuela.backend.announcement.usecase.announcement.ListMyAnnouncementsUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class MeAnnouncementController {

    private final ListMyAnnouncementsUseCase listMyAnnouncements;

    public MeAnnouncementController(ListMyAnnouncementsUseCase listMyAnnouncements) {
        this.listMyAnnouncements = listMyAnnouncements;
    }

    @GetMapping("/me/announcements")
    public List<MyAnnouncementResponse> mine() {
        return listMyAnnouncements.listForCurrentStudent().stream().map(MyAnnouncementResponse::from).toList();
    }
}
