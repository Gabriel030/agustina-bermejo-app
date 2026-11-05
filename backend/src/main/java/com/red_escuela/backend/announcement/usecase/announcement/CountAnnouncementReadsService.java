package com.red_escuela.backend.announcement.usecase.announcement;

import com.red_escuela.backend.announcement.dto.AnnouncementReadCount;
import com.red_escuela.backend.announcement.service.AnnouncementReadCounter;
import com.red_escuela.backend.shared.UseCase;

import java.util.Collection;
import java.util.Map;

@UseCase
public class CountAnnouncementReadsService implements CountAnnouncementReadsUseCase {

    private final AnnouncementReadCounter readCounter;

    public CountAnnouncementReadsService(AnnouncementReadCounter readCounter) {
        this.readCounter = readCounter;
    }

    @Override
    public Map<Long, AnnouncementReadCount> countsOf(Collection<Long> announcementIds) {
        return readCounter.countsOf(announcementIds);
    }
}
