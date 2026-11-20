package com.red_escuela.backend.notification.broadcast.usecase.consent;

import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.notification.broadcast.dto.ContactConsentEntry;
import com.red_escuela.backend.notification.broadcast.specialist.ContactConsentLedger;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ListContactConsentsService implements ListContactConsentsUseCase {

    private final CurrentActorFinder currentActorFinder;
    private final ContactConsentLedger contactConsentLedger;

    public ListContactConsentsService(CurrentActorFinder currentActorFinder, ContactConsentLedger contactConsentLedger) {
        this.currentActorFinder = currentActorFinder;
        this.contactConsentLedger = contactConsentLedger;
    }

    @Override
    public List<ContactConsentEntry> listContactConsents() {
        return contactConsentLedger.listEntries(currentActorFinder.currentPersonId());
    }
}
