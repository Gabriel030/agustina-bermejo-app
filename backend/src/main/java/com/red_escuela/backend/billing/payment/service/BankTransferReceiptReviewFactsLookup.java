package com.red_escuela.backend.billing.payment.service;

import com.red_escuela.backend.academicstructure.api.BusinessDayCalendar;
import com.red_escuela.backend.billing.operation.ReceiptReviewFacts;
import com.red_escuela.backend.billing.operation.ReceiptReviewFactsLookup;
import com.red_escuela.backend.billing.payment.domain.BusinessDayCounter;
import com.red_escuela.backend.billing.payment.repository.BankTransferReceiptRecordRepository;
import com.red_escuela.backend.billing.payment.repository.BankTransferReceiptRecordRepository.ReceiptSubmissionFacts;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class BankTransferReceiptReviewFactsLookup implements ReceiptReviewFactsLookup {
    private static final String PENDING_REVIEW_STATUS = "PENDING_REVIEW";

    private final BankTransferReceiptRecordRepository receipts;
    private final BusinessDayCalendar businessDayCalendar;
    private final Clock clock;

    public BankTransferReceiptReviewFactsLookup(BankTransferReceiptRecordRepository receipts, BusinessDayCalendar businessDayCalendar, Clock clock) {
        this.receipts = receipts;
        this.businessDayCalendar = businessDayCalendar;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, ReceiptReviewFacts> findFactsByReceiptId(Collection<Long> receiptIds) {
        if (receiptIds.isEmpty()) {
            return Map.of();
        }
        BusinessDayCounter counter = BusinessDayCounter.remembering(businessDayCalendar::isBusinessDay);
        LocalDate today = LocalDate.now(clock);
        return receipts.findSubmissionFactsByReceiptIds(receiptIds).stream()
                .collect(Collectors.toMap(ReceiptSubmissionFacts::getReceiptId, facts -> factsOf(facts, counter, today), (first, second) -> first));
    }

    private ReceiptReviewFacts factsOf(ReceiptSubmissionFacts facts, BusinessDayCounter counter, LocalDate today) {
        Money declaredAmount = facts.getDeclaredAmount() == null ? null : Money.of(facts.getDeclaredAmount(), Currency.valueOf(facts.getDeclaredAmountCurrency()));
        Integer pendingBusinessDays = PENDING_REVIEW_STATUS.equals(facts.getStatus()) ? counter.countBusinessDaysAfter(facts.getUploadedAt().toLocalDate(), today) : null;
        return new ReceiptReviewFacts(declaredAmount, facts.getTransferDate(), facts.getOperationNumber(), facts.getOriginAccount(), facts.getHolderName(),
                pendingBusinessDays, facts.getReviewAlertSentAt());
    }
}
