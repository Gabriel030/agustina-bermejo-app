package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.configuration.domain.LatePaymentTerms;
import com.red_escuela.backend.billing.configuration.domain.LatePaymentVersion;
import com.red_escuela.backend.billing.configuration.service.BillingConfigurationReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;

@Component
public class LatePaymentVersionResolver {
    private static final Logger log = LoggerFactory.getLogger(LatePaymentVersionResolver.class);

    private final BillingConfigurationReader configurationReader;

    public LatePaymentVersionResolver(BillingConfigurationReader configurationReader) {
        this.configurationReader = configurationReader;
    }

    public java.util.function.Function<LocalDate, LatePaymentTerms> termsInEffectUpTo(LocalDate lastDay, LatePaymentTerms fallback) {
        java.util.NavigableMap<LocalDate, LatePaymentVersion> timeline = configurationReader.latePaymentTimelineUpTo(lastDay);
        return day -> {
            java.util.Map.Entry<LocalDate, LatePaymentVersion> entry = timeline.floorEntry(day);
            if (entry != null) {
                return entry.getValue().terms();
            }
            return timeline.isEmpty() ? fallback : timeline.firstEntry().getValue().terms();
        };
    }

    public Optional<LatePaymentVersion> findVersionInEffectOnOrFirst(LocalDate date, Long invoiceId) {
        Optional<LatePaymentVersion> inEffect = configurationReader.findLatePaymentVersionEffectiveOn(date);
        if (inEffect.isPresent()) {
            return inEffect;
        }
        Optional<LatePaymentVersion> first = configurationReader.findFirstLatePaymentVersion();
        first.ifPresentOrElse(
                version -> log.warn("Factura id={}: no hay versión de mora vigente el {}, se usa la primera versión", invoiceId, date),
                () -> log.warn("Factura id={}: no hay ninguna versión de mora", invoiceId));
        return first;
    }
}
