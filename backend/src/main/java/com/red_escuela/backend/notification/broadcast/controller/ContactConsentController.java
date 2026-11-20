package com.red_escuela.backend.notification.broadcast.controller;

import com.red_escuela.backend.notification.broadcast.dto.ContactConsentEntry;
import com.red_escuela.backend.notification.broadcast.dto.UpdateContactConsentRequest;
import com.red_escuela.backend.notification.broadcast.usecase.consent.ListContactConsentsUseCase;
import com.red_escuela.backend.notification.broadcast.usecase.consent.UpdateContactConsentUseCase;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/me/contact-consents")
public class ContactConsentController {

    private final ListContactConsentsUseCase listContactConsentsUseCase;
    private final UpdateContactConsentUseCase updateContactConsentUseCase;

    public ContactConsentController(ListContactConsentsUseCase listContactConsentsUseCase,
                                    UpdateContactConsentUseCase updateContactConsentUseCase) {
        this.listContactConsentsUseCase = listContactConsentsUseCase;
        this.updateContactConsentUseCase = updateContactConsentUseCase;
    }

    @GetMapping
    public List<ContactConsentEntry> listContactConsents() {
        return listContactConsentsUseCase.listContactConsents();
    }

    @PutMapping
    public ContactConsentEntry updateContactConsent(@Valid @RequestBody UpdateContactConsentRequest request) {
        return updateContactConsentUseCase.updateContactConsent(request);
    }
}
