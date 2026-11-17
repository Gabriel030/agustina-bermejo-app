package com.red_escuela.backend.notification.broadcast.usecase.consent;

import com.red_escuela.backend.notification.broadcast.dto.ContactConsentEntry;

import java.util.List;

public interface ListContactConsentsUseCase {
    List<ContactConsentEntry> listContactConsents();
}
