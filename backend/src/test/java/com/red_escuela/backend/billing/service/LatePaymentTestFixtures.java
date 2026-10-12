package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.configuration.domain.ConfigurationScope;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationTopic;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersion;
import com.red_escuela.backend.billing.configuration.domain.LatePaymentTerms;
import com.red_escuela.backend.billing.configuration.repository.ConfigurationVersionRepository;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.domain.Percentage;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class LatePaymentTestFixtures {
    private LatePaymentTestFixtures() {
    }

    public static ConfigurationVersion persistLatePayment(ConfigurationVersionRepository repository, Person createdBy, int gracePeriodDays, BigDecimal surchargeFraction,
                                                          int suspensionAfterDays, int suspensionAfterInvoiceCount, LocalDate effectiveFrom) {
        return persistLatePayment(repository, createdBy, gracePeriodDays, surchargeFraction, suspensionAfterDays, suspensionAfterInvoiceCount, 10, true, false, effectiveFrom);
    }

    public static ConfigurationVersion persistLatePayment(ConfigurationVersionRepository repository, Person createdBy, int gracePeriodDays, BigDecimal surchargeFraction,
                                                          int suspensionAfterDays, int suspensionAfterInvoiceCount, int debitNoteDueDays, boolean freezeOnWithdrawal,
                                                          boolean chargingEnabled, LocalDate effectiveFrom) {
        LatePaymentTerms terms = new LatePaymentTerms(gracePeriodDays, Percentage.ofFraction(surchargeFraction), suspensionAfterDays, suspensionAfterInvoiceCount,
                true, debitNoteDueDays, freezeOnWithdrawal, chargingEnabled);
        return repository.saveAndFlush(ConfigurationVersion.publish(ConfigurationTopic.ofGlobal(ConfigurationScope.LATE_PAYMENT), terms, effectiveFrom, "Fixture de prueba",
                createdBy.getId(), LocalDate.now(), null, Instant.now()));
    }
}
