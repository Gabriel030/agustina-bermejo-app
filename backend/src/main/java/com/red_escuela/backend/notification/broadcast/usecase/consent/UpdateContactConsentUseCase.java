package com.red_escuela.backend.notification.broadcast.usecase.consent;

import com.red_escuela.backend.notification.broadcast.dto.ContactConsentEntry;
import com.red_escuela.backend.notification.broadcast.dto.UpdateContactConsentRequest;

public interface UpdateContactConsentUseCase {
    ContactConsentEntry updateContactConsent(UpdateContactConsentRequest request);
}
