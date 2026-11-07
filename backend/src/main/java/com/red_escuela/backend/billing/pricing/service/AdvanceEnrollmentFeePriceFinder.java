package com.red_escuela.backend.billing.pricing.service;

import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.pricing.domain.PriceVersion;
import com.red_escuela.backend.billing.pricing.domain.PriceVersionStatus;
import com.red_escuela.backend.billing.pricing.repository.PriceVersionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.Optional;
import java.util.Set;

@Component
public class AdvanceEnrollmentFeePriceFinder {
    private static final Set<PriceVersionStatus> LIVE = Set.of(PriceVersionStatus.SCHEDULED, PriceVersionStatus.EFFECTIVE);

    private final PriceVersionRepository versionRepository;

    public AdvanceEnrollmentFeePriceFinder(PriceVersionRepository versionRepository) {
        this.versionRepository = versionRepository;
    }

    @Transactional(readOnly = true)
    public Optional<PriceVersion> findLiveVersion(Long programId, int lectiveYear) {
        return versionRepository.findByPriceProgramIdAndPriceFeeTypeAndAppliesToLectiveYearAndStatusIn(programId, FeeType.ENROLLMENT_FEE, lectiveYear, LIVE).stream()
                .max(Comparator.comparing(PriceVersion::getEffectiveFrom));
    }
}
