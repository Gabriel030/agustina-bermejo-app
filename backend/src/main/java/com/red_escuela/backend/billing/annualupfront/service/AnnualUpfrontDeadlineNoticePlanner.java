package com.red_escuela.backend.billing.annualupfront.service;

import com.red_escuela.backend.billing.annualupfront.repository.AnnualUpfrontRequestRepository;
import com.red_escuela.backend.billing.annualupfront.repository.AnnualUpfrontRequestRepository.DeadlineNoticeCandidate;
import com.red_escuela.backend.billing.events.AnnualUpfrontDeadlineNoticePlannedEvent;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AnnualUpfrontDeadlineNoticePlanner {
    static final Duration NOTICE_LEAD = Duration.ofHours(24);

    private static final Logger LOG = LoggerFactory.getLogger(AnnualUpfrontDeadlineNoticePlanner.class);

    private final AnnualUpfrontRequestRepository requestRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public AnnualUpfrontDeadlineNoticePlanner(AnnualUpfrontRequestRepository requestRepository, ApplicationEventPublisher eventPublisher, Clock clock) {
        this.requestRepository = requestRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public int planDeadlineNotices() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<DeadlineNoticeCandidate> candidates = requestRepository.findDeadlineNoticeCandidates(now, now.plus(NOTICE_LEAD));
        int planned = 0;
        for (DeadlineNoticeCandidate candidate : candidates) {
            if (hasDeadlineOfAtLeastTheNoticeLead(candidate)) {
                eventPublisher.publishEvent(AnnualUpfrontDeadlineNoticePlannedEvent.plannedFor(candidate.getRequestId(), candidate.getInvoiceId(), candidate.getStudentPersonId(),
                        candidate.getPaymentDeadline(), Money.of(candidate.getTotal(), Currency.valueOf(candidate.getCurrency())), LocalDate.from(now)));
                planned++;
            }
        }
        LOG.info("Avisos de plazo del pago anual: candidatos={} planificados={}", candidates.size(), planned);
        return planned;
    }

    private static boolean hasDeadlineOfAtLeastTheNoticeLead(DeadlineNoticeCandidate candidate) {
        return !candidate.getRequestedAt().isAfter(candidate.getPaymentDeadline().minus(NOTICE_LEAD));
    }
}
