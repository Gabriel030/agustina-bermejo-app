package com.red_escuela.backend.notification.broadcast.usecase.unsubscribe;

import com.red_escuela.backend.identity.api.PersonExistenceChecker;
import com.red_escuela.backend.notification.broadcast.domain.ConsentSource;
import com.red_escuela.backend.notification.broadcast.domain.UnsubscribeClaims;
import com.red_escuela.backend.notification.broadcast.exception.InvalidUnsubscribeTokenException;
import com.red_escuela.backend.notification.broadcast.specialist.ContactConsentLedger;
import com.red_escuela.backend.notification.broadcast.specialist.UnsubscribeTokenVerifier;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ConfirmUnsubscribeService implements ConfirmUnsubscribeUseCase {

    private final UnsubscribeTokenVerifier unsubscribeTokenVerifier;
    private final ContactConsentLedger contactConsentLedger;
    private final PersonExistenceChecker personExistenceChecker;

    public ConfirmUnsubscribeService(UnsubscribeTokenVerifier unsubscribeTokenVerifier, ContactConsentLedger contactConsentLedger,
                                     PersonExistenceChecker personExistenceChecker) {
        this.personExistenceChecker = personExistenceChecker;
        this.unsubscribeTokenVerifier = unsubscribeTokenVerifier;
        this.contactConsentLedger = contactConsentLedger;
    }

    @Override
    public void confirmUnsubscribe(String token) {
        UnsubscribeClaims claims = unsubscribeTokenVerifier.verifyToken(token);
        if (!personExistenceChecker.personExists(claims.personId())) {
            throw new InvalidUnsubscribeTokenException();
        }
        contactConsentLedger.recordConsent(claims.personId(), claims.channel(), claims.category(), false,
                ConsentSource.EMAIL_LINK, null);
    }
}
