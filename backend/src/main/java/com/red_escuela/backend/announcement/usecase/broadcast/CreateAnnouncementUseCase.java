package com.red_escuela.backend.announcement.usecase.broadcast;

import com.red_escuela.backend.announcement.entity.Announcement;

public interface CreateAnnouncementUseCase {

    Announcement create(CreateAnnouncementCommand command);
}
