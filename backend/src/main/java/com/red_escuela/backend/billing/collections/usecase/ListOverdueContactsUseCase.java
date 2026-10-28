package com.red_escuela.backend.billing.collections.usecase;

import com.red_escuela.backend.billing.collections.dto.OverdueContactPageResponse;
import com.red_escuela.backend.billing.collections.domain.OverdueContactFilter;

public interface ListOverdueContactsUseCase {
    OverdueContactPageResponse listOverdueContacts(int page, int size, OverdueContactFilter filter);
}
