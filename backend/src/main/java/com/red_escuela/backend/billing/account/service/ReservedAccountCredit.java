package com.red_escuela.backend.billing.account.service;

import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;

public interface ReservedAccountCredit {

    NonNegativeMoney reservedOn(Long studentCurrentAccountId, Currency currency);
}
