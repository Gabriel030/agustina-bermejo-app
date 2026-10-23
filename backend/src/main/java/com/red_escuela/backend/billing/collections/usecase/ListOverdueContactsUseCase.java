package com.red_escuela.backend.billing.collections.usecase;

import com.red_escuela.backend.billing.collections.dto.OverdueContactPageResponse;
import com.red_escuela.backend.shared.domain.Currency;

public interface ListOverdueContactsUseCase {
    OverdueContactPageResponse listOverdueContacts(int page, int size, Currency currency);
}
