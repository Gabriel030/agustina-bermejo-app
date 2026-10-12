package com.red_escuela.backend.billing.pricing.service;

import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.pricing.domain.PriceVersion;
import com.red_escuela.backend.billing.pricing.domain.PriceVersionResolution;
import com.red_escuela.backend.billing.pricing.domain.PriceVersionStatus;
import com.red_escuela.backend.billing.pricing.exception.NoEffectivePriceException;
import com.red_escuela.backend.billing.pricing.exception.PriceVersionNotFoundException;
import com.red_escuela.backend.billing.pricing.repository.PriceVersionRepository;
import com.red_escuela.backend.shared.pricing.OfferingReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

@Service
public class PriceReader {
    private static final Set<PriceVersionStatus> RESOLVABLE = Set.of(PriceVersionStatus.SCHEDULED, PriceVersionStatus.EFFECTIVE, PriceVersionStatus.SUPERSEDED);

    private final PriceVersionRepository priceVersionRepository;

    public PriceReader(PriceVersionRepository priceVersionRepository) {
        this.priceVersionRepository = priceVersionRepository;
    }

    @Transactional(readOnly = true)
    public Optional<PriceVersionResolution> findPriceVersionEffectiveOn(OfferingReference offering, FeeType feeType, LocalDate date) {
        return findVersionEffectiveOn(offering, feeType, date).map(PriceVersion::resolution);
    }

    @Transactional(readOnly = true)
    public PriceVersionResolution requirePriceVersionEffectiveOn(OfferingReference offering, FeeType feeType, LocalDate date) {
        return findPriceVersionEffectiveOn(offering, feeType, date).orElseThrow(() -> new NoEffectivePriceException(feeType, date));
    }

    @Transactional(readOnly = true)
    public String findPriceDescriptionOfVersion(Long priceVersionId) {
        return priceVersionRepository.findById(priceVersionId).orElseThrow(() -> new PriceVersionNotFoundException(priceVersionId)).getPrice().getDescription();
    }

    private Optional<PriceVersion> findVersionEffectiveOn(OfferingReference offering, FeeType feeType, LocalDate date) {
        return offering.belongsToProgram()
                ? priceVersionRepository.findFirstByPriceProgramIdAndPriceFeeTypeAndStatusInAndEffectiveFromLessThanEqualOrderByEffectiveFromDescDecidedAtDescIdDesc(
                        offering.programId(), feeType, RESOLVABLE, date)
                : priceVersionRepository.findFirstByPriceCourseIdAndPriceFeeTypeAndStatusInAndEffectiveFromLessThanEqualOrderByEffectiveFromDescDecidedAtDescIdDesc(
                        offering.courseId(), feeType, RESOLVABLE, date);
    }
}
