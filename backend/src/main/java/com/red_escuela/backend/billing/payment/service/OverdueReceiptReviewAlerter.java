package com.red_escuela.backend.billing.payment.service;

import com.red_escuela.backend.academicstructure.api.BusinessDayCalendar;
import com.red_escuela.backend.billing.events.PaymentReceiptReviewOverdueEvent;
import com.red_escuela.backend.billing.payment.domain.BusinessDayCounter;
import com.red_escuela.backend.billing.payment.repository.BankTransferReceiptRecordRepository;
import com.red_escuela.backend.billing.payment.repository.BankTransferReceiptRecordRepository.ReceiptAwaitingReviewAlert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class OverdueReceiptReviewAlerter {
    private static final Logger log = LoggerFactory.getLogger(OverdueReceiptReviewAlerter.class);

    private final BankTransferReceiptRecordRepository receipts;
    private final BusinessDayCalendar businessDayCalendar;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final int alertAfterBusinessDays;

    public OverdueReceiptReviewAlerter(BankTransferReceiptRecordRepository receipts, BusinessDayCalendar businessDayCalendar,
                                       ApplicationEventPublisher eventPublisher, Clock clock,
                                       @Value("${billing.receipt-review.alert-after-business-days:3}") int alertAfterBusinessDays) {
        if (alertAfterBusinessDays < 1) {
            throw new IllegalArgumentException("alertAfterBusinessDays must be at least 1");
        }
        this.receipts = receipts;
        this.businessDayCalendar = businessDayCalendar;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.alertAfterBusinessDays = alertAfterBusinessDays;
    }

    @Transactional
    public int alertOverdueReviews() {
        LocalDate today = LocalDate.now(clock);
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime uploadedBefore = today.minusDays(alertAfterBusinessDays).plusDays(1).atStartOfDay();
        BusinessDayCounter counter = BusinessDayCounter.remembering(businessDayCalendar::isBusinessDay);
        List<ReceiptAwaitingReviewAlert> candidates = receipts.findPendingWithoutReviewAlertUploadedBefore(uploadedBefore);
        int alerted = 0;
        for (ReceiptAwaitingReviewAlert candidate : candidates) {
            LocalDate uploadedOn = candidate.getUploadedAt().toLocalDate();
            int pendingBusinessDays = counter.countBusinessDaysAfter(uploadedOn, today);
            if (pendingBusinessDays >= alertAfterBusinessDays && receipts.markReviewAlertSent(candidate.getReceiptId(), now) == 1) {
                eventPublisher.publishEvent(PaymentReceiptReviewOverdueEvent.overdueFor(candidate.getReceiptId(), candidate.getPaymentId(),
                        candidate.getInvoiceId(), uploadedOn, pendingBusinessDays));
                alerted++;
            }
        }
        log.info("Receipt review alerts: candidates={} alerted={}", candidates.size(), alerted);
        return alerted;
    }
}
