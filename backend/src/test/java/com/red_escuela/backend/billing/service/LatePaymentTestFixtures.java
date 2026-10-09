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
        LatePaymentTerms terms = new LatePaymentTerms(gracePeriodDays, Percentage.ofFraction(surchargeFraction), 12, null, suspensionAfterDays, suspensionAfterInvoiceCount,
                true, 10, true);
        return repository.saveAndFlush(ConfigurationVersion.publish(ConfigurationTopic.ofGlobal(ConfigurationScope.LATE_PAYMENT), terms, effectiveFrom, "Fixture de prueba",
                createdBy.getId(), LocalDate.now(), null, Instant.now()));
    }
}
