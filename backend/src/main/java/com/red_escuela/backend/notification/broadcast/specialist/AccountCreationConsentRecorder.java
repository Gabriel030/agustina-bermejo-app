package com.red_escuela.backend.notification.broadcast.specialist;

import com.red_escuela.backend.identity.api.AccountCreationConsentChoice;
import com.red_escuela.backend.identity.api.AccountCreationConsentPort;
import com.red_escuela.backend.notification.broadcast.domain.BroadcastCategory;
import com.red_escuela.backend.notification.broadcast.domain.ConsentChannel;
import com.red_escuela.backend.notification.broadcast.domain.ConsentSource;
import com.red_escuela.backend.notification.broadcast.exception.ConsentCategoryRepeatedException;
import com.red_escuela.backend.notification.broadcast.exception.ConsentCategoryNotOptionalException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.EnumSet;
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
        List<AccountCreationConsentChoice> optionalChoices = choices == null ? List.of() : choices;
        Set<BroadcastCategory> seen = EnumSet.noneOf(BroadcastCategory.class);
        for (AccountCreationConsentChoice choice : optionalChoices) {
            BroadcastCategory category = knownCategory(choice.category());
            if (category == BroadcastCategory.INSTITUTIONAL) {
                throw new ConsentCategoryNotOptionalException(choice.category());
            }
            if (!seen.add(category)) {
                throw new ConsentCategoryRepeatedException(choice.category());
            }
            promotionalConsentSettings.requireAllowed(category, choice.granted());
        }
        record(personId, BroadcastCategory.INSTITUTIONAL, true);
        optionalChoices.forEach(choice -> record(personId, knownCategory(choice.category()), choice.granted()));
    }

    private void record(Long personId, BroadcastCategory category, boolean granted) {
        contactConsentLedger.recordConsent(personId, ConsentChannel.EMAIL, category, granted,
                ConsentSource.ACCOUNT_CREATION, null);
    }

    private static BroadcastCategory knownCategory(String category) {
        return Arrays.stream(BroadcastCategory.values())
                .filter(candidate -> candidate.name().equals(category))
                .findFirst()
                .orElseThrow(() -> new ConsentCategoryNotOptionalException(category));
    }
}
