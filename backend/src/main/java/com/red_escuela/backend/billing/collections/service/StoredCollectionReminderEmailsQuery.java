package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.api.CollectionReminderEmailsQuery;
import com.red_escuela.backend.billing.api.StoredReminderEmails;
import com.red_escuela.backend.billing.collections.repository.CollectionReminderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class StoredCollectionReminderEmailsQuery implements CollectionReminderEmailsQuery {

    private final CollectionReminderRepository reminderRepository;

    public StoredCollectionReminderEmailsQuery(CollectionReminderRepository reminderRepository) {
        this.reminderRepository = reminderRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredReminderEmails> findEmailsOfReminder(Long reminderId) {
        return reminderRepository.findById(reminderId).map(reminder -> new StoredReminderEmails(reminder.getBusinessDate(), reminder.getEmailMessages()));
    }
}
