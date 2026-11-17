package com.red_escuela.backend.notification.broadcast.usecase.consent;

import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.notification.broadcast.domain.ConsentSource;
import com.red_escuela.backend.notification.broadcast.dto.ContactConsentEntry;
import com.red_escuela.backend.notification.broadcast.dto.UpdateContactConsentRequest;
import com.red_escuela.backend.notification.broadcast.exception.ContactConsentAlreadyRecordedException;
import com.red_escuela.backend.notification.broadcast.specialist.ContactConsentLedger;
import com.red_escuela.backend.notification.broadcast.specialist.PromotionalConsentSettings;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class UpdateContactConsentService implements UpdateContactConsentUseCase {

    private final CurrentActorFinder currentActorFinder;
    private final ContactConsentLedger contactConsentLedger;
    private final PromotionalConsentSettings promotionalConsentSettings;

    public UpdateContactConsentService(CurrentActorFinder currentActorFinder, ContactConsentLedger contactConsentLedger,
                                       PromotionalConsentSettings promotionalConsentSettings) {
        this.promotionalConsentSettings = promotionalConsentSettings;
        this.currentActorFinder = currentActorFinder;
        this.contactConsentLedger = contactConsentLedger;
    }

    @Override
    public ContactConsentEntry updateContactConsent(UpdateContactConsentRequest request) {
        Long personId = currentActorFinder.currentPersonId();
        ConsentSource source = request.source() == null ? ConsentSource.PROFILE : request.source();
        if (source == ConsentSource.ACCOUNT_CREATION) {
            requireFirstRecord(personId, request);
            promotionalConsentSettings.requireAllowed(request.category(), request.granted());
        } else if (source != ConsentSource.PROFILE) {
            throw new IllegalArgumentException("Consent source " + source + " is not accepted here");
        }
        return contactConsentLedger.recordConsentAndDescribe(personId, request.channel(),
                request.category(), request.granted(), source, null);
    }

    private void requireFirstRecord(Long personId, UpdateContactConsentRequest request) {
        if (contactConsentLedger.lastRecordedAt(personId, request.channel(), request.category()).isPresent()) {
            throw new ContactConsentAlreadyRecordedException(request.category().name());
        }
    }
}
