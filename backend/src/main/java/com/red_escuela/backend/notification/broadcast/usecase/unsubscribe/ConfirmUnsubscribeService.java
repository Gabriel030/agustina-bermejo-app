package com.red_escuela.backend.notification.broadcast.usecase.unsubscribe;

import com.red_escuela.backend.notification.broadcast.domain.ConsentSource;
import com.red_escuela.backend.notification.broadcast.domain.UnsubscribeClaims;
import com.red_escuela.backend.notification.broadcast.specialist.ContactConsentLedger;
import com.red_escuela.backend.notification.broadcast.specialist.UnsubscribeTokenVerifier;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ConfirmUnsubscribeService implements ConfirmUnsubscribeUseCase {

    private final UnsubscribeTokenVerifier unsubscribeTokenVerifier;
    private final ContactConsentLedger contactConsentLedger;

    public ConfirmUnsubscribeService(UnsubscribeTokenVerifier unsubscribeTokenVerifier, ContactConsentLedger contactConsentLedger) {
        this.unsubscribeTokenVerifier = unsubscribeTokenVerifier;
        this.contactConsentLedger = contactConsentLedger;
    }

    @Override
    public void confirmUnsubscribe(String token) {
        UnsubscribeClaims claims = unsubscribeTokenVerifier.verifyToken(token);
        contactConsentLedger.recordConsent(claims.personId(), claims.channel(), claims.category(), false,
                ConsentSource.EMAIL_LINK, null);
    }
}
