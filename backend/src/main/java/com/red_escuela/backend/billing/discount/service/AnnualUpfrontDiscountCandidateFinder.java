package com.red_escuela.backend.billing.discount.service;

import com.red_escuela.backend.billing.discount.domain.DiscountDefinition;
import com.red_escuela.backend.billing.discount.domain.DiscountSavingCalculator;
import com.red_escuela.backend.billing.discount.repository.DiscountDefinitionRepository;
import com.red_escuela.backend.billing.discount.repository.StudentDiscountRepository;
import com.red_escuela.backend.billing.invoice.domain.DiscountType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.invoice.domain.issuing.DiscountCandidateOrigin;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceDiscountCandidate;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.LongUnaryOperator;
import java.util.stream.IntStream;

@Component
public class AnnualUpfrontDiscountCandidateFinder {
    private static final int MAX_NAME_LENGTH = 200;

    private final DiscountDefinitionRepository definitionRepository;
    private final StudentDiscountRepository studentDiscountRepository;

    public AnnualUpfrontDiscountCandidateFinder(DiscountDefinitionRepository definitionRepository, StudentDiscountRepository studentDiscountRepository) {
        this.definitionRepository = definitionRepository;
        this.studentDiscountRepository = studentDiscountRepository;
    }

    public List<InvoiceDiscountCandidate> findCandidatesForLines(Long programId, Currency currency, LocalDate date, List<NonNegativeMoney> lineBaseAmounts) {
        return candidatesOf(definitionRepository.findActiveAnnualUpfrontDefinitions(), programId, currency, date, lineBaseAmounts,
                studentDiscountRepository::countByDiscountDefinitionId);
    }

    public static List<InvoiceDiscountCandidate> candidatesOf(List<DiscountDefinition> definitions, Long programId, Currency currency, LocalDate date,
                                                              List<NonNegativeMoney> lineBaseAmounts, LongUnaryOperator placesUsedOf) {
        return definitions.stream()
                .filter(definition -> definition.isAvailableForAcquisitionOn(date))
                .filter(definition -> definition.getProgramId() == null || definition.getProgramId().equals(programId))
                .filter(definition -> definition.getCalculation() != null && hasCurrency(definition.getCalculation(), currency))
                .filter(definition -> definition.hasPlaceLeft(placesUsedOf.applyAsLong(definition.getId())))
                .flatMap(definition -> IntStream.range(0, lineBaseAmounts.size()).mapToObj(index -> candidateOf(definition, index + 1, lineBaseAmounts.get(index))))
                .filter(candidate -> !candidate.discountAmount().value().isZero())
                .toList();
    }

    private static boolean hasCurrency(MonetaryAdjustment calculation, Currency currency) {
        return !(calculation instanceof MonetaryAdjustment.Nominal nominal) || nominal.amount().currency() == currency;
    }

    private static InvoiceDiscountCandidate candidateOf(DiscountDefinition definition, int lineNumber, NonNegativeMoney base) {
        NonNegativeMoney saving = DiscountSavingCalculator.calculateSavingOnFinalPrice(definition.getCalculation(), base, Optional.ofNullable(definition.getCapPerInvoice()));
        String name = definition.getName();
        return new InvoiceDiscountCandidate(DiscountType.ANNUAL_UPFRONT, definition.getId(), null, name.substring(0, Math.min(name.length(), MAX_NAME_LENGTH)),
                InvoiceType.ANNUAL_UPFRONT, base, saving, null, null, lineNumber, definition.getPriority(), definition.getCreatedAt(), definition.getCalculation(),
                DiscountCandidateOrigin.ANNUAL_UPFRONT_REQUEST, definition.getId());
    }
}
