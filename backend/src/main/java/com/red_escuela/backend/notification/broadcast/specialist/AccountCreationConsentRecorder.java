package com.red_escuela.backend.notification.broadcast.specialist;

import com.red_escuela.backend.identity.api.AccountCreationConsentChoice;
import com.red_escuela.backend.identity.api.AccountCreationConsentPort;
import com.red_escuela.backend.notification.broadcast.domain.BroadcastCategory;
import com.red_escuela.backend.notification.broadcast.domain.ConsentChannel;
import com.red_escuela.backend.notification.broadcast.domain.ConsentSource;
import com.red_escuela.backend.notification.broadcast.exception.ConsentCategoryNotOptionalException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class AccountCreationConsentRecorder implements AccountCreationConsentPort {

    private final ContactConsentLedger contactConsentLedger;
    private final PromotionalConsentSettings promotionalConsentSettings;

    public AccountCreationConsentRecorder(ContactConsentLedger contactConsentLedger,
                                          PromotionalConsentSettings promotionalConsentSettings) {
        this.contactConsentLedger = contactConsentLedger;
        this.promotionalConsentSettings = promotionalConsentSettings;
    }

    @Override
    @Transactional
    public void recordAccountCreationConsents(Long personId, List<AccountCreationConsentChoice> choices) {
        if (choices == null || choices.isEmpty()) {
            return;
        }
        Set<BroadcastCategory> seen = new HashSet<>();
        for (AccountCreationConsentChoice choice : choices) {
            BroadcastCategory category = optionalCategory(choice.category());
            if (!seen.add(category)) {
                throw new IllegalArgumentException("Duplicate consent category " + category);
            }
            promotionalConsentSettings.requireAllowed(category, choice.granted());
        }
        choices.forEach(choice -> contactConsentLedger.recordConsent(personId, ConsentChannel.EMAIL,
                optionalCategory(choice.category()), choice.granted(), ConsentSource.ACCOUNT_CREATION, null));
    }

    private static BroadcastCategory optionalCategory(String category) {
        return Arrays.stream(BroadcastCategory.values())
                .filter(candidate -> candidate.name().equals(category))
                .findFirst()
                .orElseThrow(() -> new ConsentCategoryNotOptionalException(category));
    }
}
