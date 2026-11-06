package com.red_escuela.backend.announcement.usecase.announcement;

import com.red_escuela.backend.announcement.service.StudentAnnouncementReadTimes;
import com.red_escuela.backend.shared.UseCase;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;

@UseCase
public class ListMyAnnouncementReadTimesService implements ListMyAnnouncementReadTimesUseCase {

    private final StudentAnnouncementReadTimes readTimes;

    public ListMyAnnouncementReadTimesService(StudentAnnouncementReadTimes readTimes) {
        this.readTimes = readTimes;
    }

    @Override
    public Map<Long, LocalDateTime> readTimesOf(Collection<Long> announcementIds) {
        return readTimes.readTimesOfCurrentStudent(announcementIds);
    }
}
