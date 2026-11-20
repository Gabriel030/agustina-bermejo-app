package com.red_escuela.backend.identity.specialist;

import com.red_escuela.backend.identity.entity.ContactChangeLog;
import com.red_escuela.backend.identity.entity.ContactChangeType;
import com.red_escuela.backend.identity.repository.ContactChangeLogRepository;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Service
public class ContactChangeRecorder {

    private static final Duration RECENT_EMAIL_CHANGE_WINDOW = Duration.ofHours(72);

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
                List.of(ContactChangeType.EMAIL_CHANGE_CONFIRMED),
                ApplicationTime.now().minus(RECENT_EMAIL_CHANGE_WINDOW));
    }

    public Optional<ContactChangeLog> lastEmailChangeRequest(Long personId) {
        return contactChangeLogRepository.findFirstByPersonIdAndChangeTypeOrderByIdDesc(personId,
                ContactChangeType.EMAIL_CHANGE_REQUESTED);
    }

    public Optional<Long> lastEmailChangeRequesterId(Long personId) {
        return contactChangeLogRepository.findFirstByPersonIdAndChangeTypeOrderByIdDesc(personId,
                ContactChangeType.EMAIL_CHANGE_REQUESTED).map(ContactChangeLog::getActorPersonId);
    }
}
