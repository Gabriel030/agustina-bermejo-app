package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.api.CollectionReminderApplicabilityQuery;
import com.red_escuela.backend.billing.collections.domain.CollectionReminder;
import com.red_escuela.backend.billing.collections.domain.ReminderInvoiceFact;
import com.red_escuela.backend.billing.collections.repository.CollectionReminderRepository;
import com.red_escuela.backend.billing.events.PlannedReminderLine;
import com.red_escuela.backend.billing.events.PlannedReminderMessage;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class StoredCollectionReminderApplicabilityQuery implements CollectionReminderApplicabilityQuery {

    private final CollectionReminderRepository reminderRepository;
    private final CollectionReminderFactsLoader factsLoader;
    private final PolicySet<ReminderInvoiceFact> eligibilityPolicySet;

    public StoredCollectionReminderApplicabilityQuery(CollectionReminderRepository reminderRepository, CollectionReminderFactsLoader factsLoader,
                                                      PolicySet<ReminderInvoiceFact> collectionReminderEligibilityPolicySet) {
        this.reminderRepository = reminderRepository;
        this.factsLoader = factsLoader;
        this.eligibilityPolicySet = collectionReminderEligibilityPolicySet;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isStillApplicable(Long reminderId, LocalDate date) {
        return reminderRepository.findById(reminderId).map(reminder -> hasRemindableInvoice(reminder, date)).orElse(false);
    }

    private boolean hasRemindableInvoice(CollectionReminder reminder, LocalDate date) {
        Set<Long> noticedInvoiceIds = reminder.getEmailMessages().stream().map(PlannedReminderMessage::lines).flatMap(List::stream)
                .filter(PlannedReminderLine::isHighlighted).map(PlannedReminderLine::invoiceId).collect(Collectors.toSet());
        return factsLoader.loadInvoiceFactsOf(noticedInvoiceIds, date).stream()
                .anyMatch(fact -> eligibilityPolicySet.evaluateRulesUntilFirstViolation(fact).firstDenial().isEmpty());
    }
}
