package com.red_escuela.backend.announcement.usecase.announcement;

import com.red_escuela.backend.announcement.dto.AnnouncementReadCount;

import java.util.Collection;
import java.util.Map;

public interface CountAnnouncementReadsUseCase {

    Map<Long, AnnouncementReadCount> countsOf(Collection<Long> announcementIds);
}
