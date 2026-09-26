package com.red_escuela.backend.announcement.usecase.announcement;

import com.red_escuela.backend.announcement.entity.Announcement;

import java.util.List;

public interface ListMyAnnouncementsUseCase {

    List<Announcement> listForCurrentStudent();
}
