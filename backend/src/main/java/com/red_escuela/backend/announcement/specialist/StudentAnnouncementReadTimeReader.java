package com.red_escuela.backend.announcement.specialist;

import com.red_escuela.backend.announcement.repository.AnnouncementRecipientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Service
public class StudentAnnouncementReadTimeReader {

    private final AnnouncementViewer announcementViewer;
    private final AnnouncementRecipientRepository recipientRepository;

    public StudentAnnouncementReadTimeReader(AnnouncementViewer announcementViewer,
                                        AnnouncementRecipientRepository recipientRepository) {
        this.announcementViewer = announcementViewer;
        this.recipientRepository = recipientRepository;
    }

    @Transactional(readOnly = true)
    public Map<Long, LocalDateTime> readTimesOfCurrentStudent(Collection<Long> announcementIds) {
        Map<Long, LocalDateTime> times = new HashMap<>();
        if (announcementIds.isEmpty()) {
            return times;
        }
        announcementViewer.currentStudentId().ifPresent(studentId ->
                recipientRepository.findReadTimes(studentId, announcementIds)
                        .forEach(row -> times.put(row.getAnnouncementId(), row.getReadAt())));
        return times;
    }
}
