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
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.Set;

@Service
public class PriceReader {
    private static final Set<PriceVersionStatus> RESOLVABLE = Set.of(PriceVersionStatus.SCHEDULED, PriceVersionStatus.EFFECTIVE, PriceVersionStatus.SUPERSEDED);

    private final PriceVersionRepository priceVersionRepository;
    private final AdvanceEnrollmentFeePriceFinder advanceEnrollmentFeePriceFinder;

    public PriceReader(PriceVersionRepository priceVersionRepository, AdvanceEnrollmentFeePriceFinder advanceEnrollmentFeePriceFinder) {
        this.priceVersionRepository = priceVersionRepository;
        this.advanceEnrollmentFeePriceFinder = advanceEnrollmentFeePriceFinder;
    }

    @Transactional(readOnly = true)
    public Optional<PriceVersionResolution> findAdvanceEnrollmentFeePriceVersion(Long programId, int lectiveYear) {
        return advanceEnrollmentFeePriceFinder.findLiveVersion(programId, lectiveYear).map(PriceVersion::resolution);
    }

    @Transactional(readOnly = true)
    public Optional<PriceVersionResolution> findPriceVersionEffectiveOn(OfferingReference offering, FeeType feeType, LocalDate date) {
        return findVersionEffectiveOn(offering, feeType, date).map(PriceVersion::resolution);
    }

    @Transactional(readOnly = true)
    public Map<Long, PriceVersionResolution> findPriceVersionsEffectiveOn(Collection<Long> offeringIds, boolean programs, FeeType feeType, LocalDate date) {
        if (offeringIds.isEmpty()) {
            return Map.of();
        }
        List<PriceVersion> candidates = programs
                ? priceVersionRepository.findResolvableByProgramIds(offeringIds, feeType, RESOLVABLE, date)
                : priceVersionRepository.findResolvableByCourseIds(offeringIds, feeType, RESOLVABLE, date);
        Comparator<PriceVersion> newestFirst = Comparator.comparing(PriceVersion::getEffectiveFrom)
                .thenComparing(PriceVersion::getDecidedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(PriceVersion::getId);
        Map<Long, PriceVersionResolution> resolutions = new HashMap<>();
        candidates.stream()
                .collect(Collectors.groupingBy(version -> programs ? version.getPrice().getProgramId() : version.getPrice().getCourseId(),
                        Collectors.maxBy(newestFirst)))
                .forEach((offeringId, version) -> version.ifPresent(found -> resolutions.put(offeringId, found.resolution())));
        return resolutions;
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
