package com.red_escuela.backend.notification.delivery.service;

import com.red_escuela.backend.identity.api.EmailRecipient;
import com.red_escuela.backend.identity.api.PersonContactFinder;
import com.red_escuela.backend.shared.mail.MaskedEmailAddress;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SimulatedEmailAddressMaskFinder {
    private static final String NO_RECIPIENT_ADDRESS = "-";

    private final PersonContactFinder personContactFinder;

    public SimulatedEmailAddressMaskFinder(PersonContactFinder personContactFinder) {
        this.personContactFinder = personContactFinder;
    }

    @Transactional(readOnly = true)
    public String findMaskedAddress(Long recipientPersonId) {
        return personContactFinder.findEmailRecipient(recipientPersonId)
                .map(EmailRecipient::address).map(MaskedEmailAddress::of).orElse(NO_RECIPIENT_ADDRESS);
    }
}
