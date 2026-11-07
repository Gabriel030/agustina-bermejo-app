package com.red_escuela.backend.announcement.usecase.broadcast;

import com.red_escuela.backend.announcement.specialist.StudentAnnouncementReadTimeReader;
import com.red_escuela.backend.shared.UseCase;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;

@UseCase
public class ListMyAnnouncementReadTimesService implements ListMyAnnouncementReadTimesUseCase {

    private final StudentAnnouncementReadTimeReader readTimes;

    public ListMyAnnouncementReadTimesService(StudentAnnouncementReadTimeReader readTimes) {
        this.readTimes = readTimes;
    }

    @Override
    public Map<Long, LocalDateTime> readTimesOf(Collection<Long> announcementIds) {
        return readTimes.readTimesOfCurrentStudent(announcementIds);
    }
}
