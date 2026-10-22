package com.red_escuela.backend.billing.discount.service;

import com.red_escuela.backend.billing.discount.domain.DiscountDefinition;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionDraft;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionStatus;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionType;
import com.red_escuela.backend.billing.discount.domain.DistinctBenefitFeeTypes;
import com.red_escuela.backend.billing.discount.dto.CreatePromotionBenefitRequest;
import com.red_escuela.backend.billing.discount.dto.CreatePromotionRequest;
import com.red_escuela.backend.billing.discount.dto.PromotionBenefitResponse;
import com.red_escuela.backend.billing.discount.dto.PromotionResponse;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.identity.api.PersonNameLookup;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class PromotionContractTranslator {
    private static final int DEFAULT_PRIORITY = 1;
    private static final Duration SAME_REQUEST_WINDOW = Duration.ofSeconds(2);

    private final PersonNameLookup personNames;
    private final Clock clock;

    public PromotionContractTranslator(PersonNameLookup personNames, Clock clock) {
        this.personNames = personNames;
        this.clock = clock;
    }

    public List<DiscountDefinitionDraft> toDraftsOfProgram(Long programId, CreatePromotionRequest request) {
        return toDrafts(request, programId, null);
    }

    public List<DiscountDefinitionDraft> toDraftsOfCourse(Long courseId, CreatePromotionRequest request) {
        return toDrafts(request, null, courseId);
    }

    public List<PromotionResponse> toResponses(List<DiscountDefinition> definitions) {
        List<List<DiscountDefinition>> groups = groupsOf(definitions);
        Set<Long> personIds = new HashSet<>();
        definitions.forEach(definition -> {
            personIds.add(definition.getCreatedById());
            if (definition.getClosedById() != null) {
                personIds.add(definition.getClosedById());
            }
        });
        Map<Long, String> names = personNames.findFullNamesByPersonId(personIds);
        return groups.stream().map(group -> responseOf(group, names)).toList();
    }

    public List<DiscountDefinition> findSiblingsOf(DiscountDefinition definition, List<DiscountDefinition> activeCohortPromotions) {
        return groupsOf(activeCohortPromotions).stream().filter(group -> group.stream().anyMatch(member -> member.getId().equals(definition.getId()))).findFirst().orElse(List.of(definition));
    }

    private List<List<DiscountDefinition>> groupsOf(List<DiscountDefinition> definitions) {
        Map<PromotionGroupKey, List<DiscountDefinition>> byKey = definitions.stream().sorted(Comparator.comparing(DiscountDefinition::getId))
                .collect(Collectors.groupingBy(PromotionGroupKey::of, LinkedHashMap::new, Collectors.toList()));
        List<List<DiscountDefinition>> groups = new ArrayList<>();
        byKey.values().forEach(sameKey -> {
            List<DiscountDefinition> current = new ArrayList<>();
            for (DiscountDefinition definition : sameKey) {
                if (!current.isEmpty() && startsAnotherPromotion(current, definition)) {
                    groups.add(current);
                    current = new ArrayList<>();
                }
                current.add(definition);
            }
            groups.add(current);
        });
        return groups;
    }

    private boolean startsAnotherPromotion(List<DiscountDefinition> current, DiscountDefinition definition) {
        boolean repeatsInvoiceType = current.stream().anyMatch(member -> member.getApplicableInvoiceType() == definition.getApplicableInvoiceType());
        Instant previous = current.get(current.size() - 1).getCreatedAt();
        return repeatsInvoiceType || Duration.between(previous, definition.getCreatedAt()).compareTo(SAME_REQUEST_WINDOW) > 0;
    }

    private List<DiscountDefinitionDraft> toDrafts(CreatePromotionRequest request, Long programId, Long courseId) {
        new DistinctBenefitFeeTypes(request.benefits().stream().map(CreatePromotionBenefitRequest::feeType).toList());
        List<DiscountDefinitionDraft> drafts = new ArrayList<>();
        for (CreatePromotionBenefitRequest benefit : request.benefits()) {
            InvoiceType invoiceType = invoiceTypeOf(benefit.feeType());
            Integer installments = invoiceType == InvoiceType.MONTHLY_INSTALLMENT ? benefit.invoiceScopeCount() : null;
            drafts.add(new DiscountDefinitionDraft(request.description(), DiscountDefinitionType.COHORT_PROMOTION, invoiceType, benefit.value().toAdjustment(), installments,
                    request.eligibilityValidFrom(), null, DEFAULT_PRIORITY, null, programId, null, courseId, request.eligibilityValidFrom(), request.eligibilityValidTo(), null));
        }
        return drafts;
    }

    private PromotionResponse responseOf(List<DiscountDefinition> group, Map<Long, String> names) {
        DiscountDefinition first = group.get(0);
        boolean closed = group.stream().allMatch(definition -> definition.getStatus() == DiscountDefinitionStatus.CLOSED);
        return new PromotionResponse(first.getId(), first.getName(), first.getEnrollmentWindowFrom(), first.getEnrollmentWindowTo(), closed ? "CLOSED" : "ACTIVE",
                names.get(first.getCreatedById()), localOf(first.getCreatedAt()), closed ? names.get(first.getClosedById()) : null,
                closed ? localOf(first.getClosedAt()) : null, group.stream().map(PromotionBenefitResponse::from).toList());
    }

    private LocalDateTime localOf(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, clock.getZone());
    }

    private InvoiceType invoiceTypeOf(FeeType feeType) {
        return switch (feeType) {
            case MONTHLY_INSTALLMENT -> InvoiceType.MONTHLY_INSTALLMENT;
            case ENROLLMENT_FEE -> InvoiceType.ENROLLMENT_FEE;
            case EXAM_FEE -> InvoiceType.EXAM_FEE;
            case RETAKEN_SUBJECT_FEE -> InvoiceType.RETAKEN_SUBJECT_FEE;
            case DEGREE_ISSUANCE_FEE -> InvoiceType.DEGREE_ISSUANCE_FEE;
            case ADMINISTRATIVE_FEE -> InvoiceType.ADMINISTRATIVE_FEE;
            case LATE_FEE -> InvoiceType.LATE_FEE;
        };
    }

    private record PromotionGroupKey(String name, Long programId, Long courseId, LocalDate availableFrom, LocalDate windowFrom, LocalDate windowTo, Long createdById) {

        static PromotionGroupKey of(DiscountDefinition definition) {
            return new PromotionGroupKey(definition.getName(), definition.getProgramId(), definition.getCourseId(), definition.getAvailableFrom(),
                    definition.getEnrollmentWindowFrom(), definition.getEnrollmentWindowTo(), definition.getCreatedById());
        }
    }
}
