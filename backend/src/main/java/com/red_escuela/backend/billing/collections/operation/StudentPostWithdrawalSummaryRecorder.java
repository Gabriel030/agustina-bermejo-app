package com.red_escuela.backend.billing.collections.operation;

import com.red_escuela.backend.billing.collections.domain.PostWithdrawalSummaryCandidate;
import com.red_escuela.backend.billing.collections.domain.WithdrawnEnrollmentDebtLine;
import com.red_escuela.backend.billing.collections.repository.PostWithdrawalSummaryStore;
import com.red_escuela.backend.billing.events.PlannedReminderInvoiceKind;
import com.red_escuela.backend.billing.events.PostWithdrawalSummaryPlannedEvent;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class StudentPostWithdrawalSummaryRecorder {

    private final PostWithdrawalSummaryStore summaryStore;
    private final ApplicationEventPublisher eventPublisher;

    public StudentPostWithdrawalSummaryRecorder(PostWithdrawalSummaryStore summaryStore, ApplicationEventPublisher eventPublisher) {
        this.summaryStore = summaryStore;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSummaryOf(PostWithdrawalSummaryCandidate candidate, LocalDate businessDate) {
        Set<Long> programEnrollmentIds = new LinkedHashSet<>();
        Set<Long> courseEnrollmentIds = new LinkedHashSet<>();
        for (WithdrawnEnrollmentDebtLine line : candidate.lines()) {
            if (line.programEnrollmentId() != null) {
                programEnrollmentIds.add(line.programEnrollmentId());
            } else {
                courseEnrollmentIds.add(line.courseEnrollmentId());
            }
        }
        programEnrollmentIds.forEach(id -> summaryStore.recordSummaryOf(candidate.studentId(), id, null, businessDate));
        courseEnrollmentIds.forEach(id -> summaryStore.recordSummaryOf(candidate.studentId(), null, id, businessDate));
        eventPublisher.publishEvent(PostWithdrawalSummaryPlannedEvent.plannedFor(candidate.personId(), businessDate, blocksOf(candidate)));
    }

    private List<PostWithdrawalSummaryPlannedEvent.Block> blocksOf(PostWithdrawalSummaryCandidate candidate) {
        Map<Currency, List<PostWithdrawalSummaryPlannedEvent.Line>> linesByCurrency = new LinkedHashMap<>();
        Map<Currency, Money> totalsByCurrency = new LinkedHashMap<>();
        for (WithdrawnEnrollmentDebtLine line : candidate.distinctInvoiceLines()) {
            Currency currency = line.balance().currency();
            PlannedReminderInvoiceKind kind = line.enrollmentFee() ? PlannedReminderInvoiceKind.ENROLLMENT_FEE : PlannedReminderInvoiceKind.MONTHLY_INSTALLMENT;
            linesByCurrency.computeIfAbsent(currency, key -> new ArrayList<>()).add(new PostWithdrawalSummaryPlannedEvent.Line(kind, line.dueDate(), line.balance()));
            totalsByCurrency.merge(currency, line.balance(), Money::plus);
        }
        return linesByCurrency.entrySet().stream()
                .map(entry -> new PostWithdrawalSummaryPlannedEvent.Block(entry.getValue(), totalsByCurrency.get(entry.getKey()))).toList();
    }
}
