package com.red_escuela.backend.notification.broadcast.usecase.consent;

import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.notification.broadcast.domain.ConsentSource;
import com.red_escuela.backend.notification.broadcast.dto.ContactConsentEntry;
import com.red_escuela.backend.notification.broadcast.dto.UpdateContactConsentRequest;
import com.red_escuela.backend.notification.broadcast.exception.ConsentSourceNotAcceptedException;
import com.red_escuela.backend.notification.broadcast.specialist.ContactConsentLedger;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class UpdateContactConsentService implements UpdateContactConsentUseCase {

    private final CurrentActorFinder currentActorFinder;
    private final ContactConsentLedger contactConsentLedger;

    public UpdateContactConsentService(CurrentActorFinder currentActorFinder, ContactConsentLedger contactConsentLedger) {
        this.currentActorFinder = currentActorFinder;
        this.contactConsentLedger = contactConsentLedger;
    }

    @Override
    public ContactConsentEntry updateContactConsent(UpdateContactConsentRequest request) {
        if (request.source() != null && request.source() != ConsentSource.PROFILE) {
            throw new ConsentSourceNotAcceptedException(request.source().name());
        }
        return contactConsentLedger.recordConsentAndDescribe(currentActorFinder.currentPersonId(), request.channel(),
                request.category(), request.granted(), ConsentSource.PROFILE, null);
    }
}
