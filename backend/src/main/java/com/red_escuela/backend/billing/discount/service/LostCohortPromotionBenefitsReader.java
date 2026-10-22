package com.red_escuela.backend.billing.discount.service;

import com.red_escuela.backend.billing.discount.domain.AppliedStudentDiscountInstallment;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinition;
import com.red_escuela.backend.billing.discount.domain.StudentDiscount;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountOrigin;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountStatus;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountStatusChange;
import com.red_escuela.backend.billing.discount.repository.AppliedStudentDiscountRepository;
import com.red_escuela.backend.billing.discount.repository.DiscountDefinitionRepository;
import com.red_escuela.backend.billing.discount.repository.StudentDiscountRepository;
import com.red_escuela.backend.billing.discount.dto.LostPromotionBenefitResponse;
import com.red_escuela.backend.identity.api.StudentNameLookup;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class LostCohortPromotionBenefitsReader {
    private static final Set<StudentDiscountStatus> CANDIDATE_STATUSES = Set.of(StudentDiscountStatus.ACTIVE, StudentDiscountStatus.ENDED);

    private final StudentDiscountRepository repository;
    private final DiscountDefinitionRepository definitionRepository;
    private final AppliedStudentDiscountRepository appliedRepository;
    private final StudentNameLookup studentNames;
    private final Clock clock;

    public LostCohortPromotionBenefitsReader(StudentDiscountRepository repository, DiscountDefinitionRepository definitionRepository,
                                                  AppliedStudentDiscountRepository appliedRepository, StudentNameLookup studentNames, Clock clock) {
        this.repository = repository;
        this.definitionRepository = definitionRepository;
        this.appliedRepository = appliedRepository;
        this.studentNames = studentNames;
        this.clock = clock;
    }

    public List<LostPromotionBenefitResponse> readLostBenefits() {
        List<StudentDiscount> promotions = repository.findByOriginAndStatusInOrderByIdAsc(StudentDiscountOrigin.COHORT_ACQUISITION, CANDIDATE_STATUSES).stream()
                .filter(promotion -> promotion.getInstallmentsTotal() != null).toList();
        if (promotions.isEmpty()) {
            return List.of();
        }
        Map<Long, DiscountDefinition> definitions = definitionRepository.findAllById(promotions.stream().map(StudentDiscount::getDiscountDefinitionId).distinct().toList()).stream()
                .collect(Collectors.toMap(DiscountDefinition::getId, Function.identity()));
        Map<Long, Long> usesById = appliedRepository.findLiveApplicationsOf(promotions.stream().map(StudentDiscount::getId).toList()).stream()
                .collect(Collectors.groupingBy(AppliedStudentDiscountInstallment::studentDiscountId, Collectors.counting()));
        Map<Long, String> names = studentNames.findFullNamesByStudentId(promotions.stream().map(StudentDiscount::getStudentId).distinct().toList());
        LocalDate today = LocalDate.now(clock);
        return promotions.stream()
                .flatMap(promotion -> lostBenefitOf(promotion, definitions.get(promotion.getDiscountDefinitionId()), usesById.getOrDefault(promotion.getId(), 0L), names, today).stream())
                .toList();
    }

    private Optional<LostPromotionBenefitResponse> lostBenefitOf(StudentDiscount promotion, DiscountDefinition definition, long used, Map<Long, String> names, LocalDate today) {
        int remaining = (int) (promotion.getInstallmentsTotal() - used);
        if (remaining <= 0) {
            return Optional.empty();
        }
        return findCutOff(promotion, definition, today).map(cutOff -> new LostPromotionBenefitResponse(promotion.getId(), definition.getName(), names.get(promotion.getStudentId()),
                promotion.getApplicableInvoiceType().name(), (int) used, promotion.getInstallmentsTotal(), remaining, cutOff));
    }

    private Optional<YearMonth> findCutOff(StudentDiscount promotion, DiscountDefinition definition, LocalDate today) {
        if (promotion.getStatus() == StudentDiscountStatus.ENDED) {
            return promotion.getStatusChanges().stream().filter(change -> change.getToStatus() == StudentDiscountStatus.ENDED)
                    .map(StudentDiscountStatusChange::getChangedAt).reduce((first, second) -> second)
                    .map(changedAt -> YearMonth.from(changedAt.atZone(clock.getZone())));
        }
        LocalDate until = definition.getAvailableUntil();
        return until != null && today.isAfter(until) ? Optional.of(YearMonth.from(until)) : Optional.empty();
    }
}
