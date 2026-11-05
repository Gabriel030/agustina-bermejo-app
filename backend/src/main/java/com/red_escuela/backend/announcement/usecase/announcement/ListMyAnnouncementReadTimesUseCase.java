package com.red_escuela.backend.announcement.usecase.announcement;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;

public interface ListMyAnnouncementReadTimesUseCase {

    Map<Long, LocalDateTime> readTimesOf(Collection<Long> announcementIds);
}
