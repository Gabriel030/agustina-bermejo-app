package com.red_escuela.backend.identity.api;

import java.util.List;

public interface AccountCreationConsentPort {
    void recordAccountCreationConsents(Long personId, List<AccountCreationConsentChoice> choices);
}
