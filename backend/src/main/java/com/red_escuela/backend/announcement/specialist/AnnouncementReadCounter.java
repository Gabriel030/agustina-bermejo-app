package com.red_escuela.backend.announcement.specialist;

import com.red_escuela.backend.announcement.repository.AnnouncementRecipientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Service
public class AnnouncementReadCounter {

    public record ReadCount(long recipientCount, long readCount) {
    }

    private final AnnouncementRecipientRepository recipientRepository;

    public AnnouncementReadCounter(AnnouncementRecipientRepository recipientRepository) {
        this.recipientRepository = recipientRepository;
    }

    @Transactional(readOnly = true)
    public Map<Long, ReadCount> countsOf(Collection<Long> announcementIds) {
        Map<Long, ReadCount> counts = new HashMap<>();
        if (announcementIds.isEmpty()) {
            return counts;
        }
        recipientRepository.countReadByAnnouncementIds(announcementIds).forEach(row ->
                counts.put(row.getAnnouncementId(), new ReadCount(row.getTotal(), row.getReadCount())));
        return counts;
    }
}
