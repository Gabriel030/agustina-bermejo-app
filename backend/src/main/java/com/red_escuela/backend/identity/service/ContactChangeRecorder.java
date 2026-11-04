package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.identity.entity.ContactChangeLog;
import com.red_escuela.backend.identity.entity.ContactChangeType;
import com.red_escuela.backend.identity.repository.ContactChangeLogRepository;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class ContactChangeRecorder {

    private static final Duration RECENT_EMAIL_CHANGE_WINDOW = Duration.ofHours(24);

    private final ContactChangeLogRepository contactChangeLogRepository;

    public ContactChangeRecorder(ContactChangeLogRepository contactChangeLogRepository) {
        this.contactChangeLogRepository = contactChangeLogRepository;
    }

    public void recordChange(Long personId, Long actorPersonId, ContactChangeType changeType, String oldValue,
                             String newValue) {
        contactChangeLogRepository.save(new ContactChangeLog(personId, actorPersonId, changeType, oldValue, newValue,
                ApplicationTime.now()));
    }

    public boolean hasRecentEmailChange(Long personId) {
        return contactChangeLogRepository.existsByPersonIdAndChangeTypeInAndOccurredAtAfter(personId,
                List.of(ContactChangeType.EMAIL_CHANGE_REQUESTED, ContactChangeType.EMAIL_CHANGE_CONFIRMED),
                ApplicationTime.now().minus(RECENT_EMAIL_CHANGE_WINDOW));
    }
}
