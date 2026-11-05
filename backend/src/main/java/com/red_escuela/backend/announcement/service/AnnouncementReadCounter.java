package com.red_escuela.backend.announcement.service;

import com.red_escuela.backend.announcement.dto.AnnouncementReadCount;
import com.red_escuela.backend.announcement.repository.AnnouncementRecipientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Service
public class AnnouncementReadCounter {

    private final AnnouncementRecipientRepository recipientRepository;

    public AnnouncementReadCounter(AnnouncementRecipientRepository recipientRepository) {
        this.recipientRepository = recipientRepository;
    }

    @Transactional(readOnly = true)
    public Map<Long, AnnouncementReadCount> countsOf(Collection<Long> announcementIds) {
        Map<Long, AnnouncementReadCount> counts = new HashMap<>();
        if (announcementIds.isEmpty()) {
            return counts;
        }
        recipientRepository.countReadByAnnouncementIds(announcementIds).forEach(row ->
                counts.put(row.getAnnouncementId(), new AnnouncementReadCount(row.getTotal(), row.getReadCount())));
        return counts;
    }
}
