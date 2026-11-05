package com.red_escuela.backend.billing.collections.operation;

import com.red_escuela.backend.billing.collections.domain.CollectionReminder;
import com.red_escuela.backend.billing.collections.domain.DailyReminderDigest;
import com.red_escuela.backend.billing.collections.domain.FiredReminderOccurrence;
import com.red_escuela.backend.billing.collections.domain.MissedReminderRecovery;
import com.red_escuela.backend.billing.collections.domain.NonBusinessDays;
import com.red_escuela.backend.billing.collections.domain.ReminderContentTerms;
import com.red_escuela.backend.billing.collections.domain.ReminderDigestMode;
import com.red_escuela.backend.billing.collections.repository.CollectionReminderRepository;
import com.red_escuela.backend.billing.collections.service.StudentReminderContext;
import com.red_escuela.backend.billing.configuration.domain.ResolvedReminderSchedule;
import com.red_escuela.backend.billing.configuration.domain.ReminderChannel;
import com.red_escuela.backend.billing.events.PlannedReminderLine;
import com.red_escuela.backend.billing.events.PlannedReminderMessage;
import com.red_escuela.backend.billing.events.PlannedReminderNotice;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class StudentCollectionReminderPlanner {
    private static final Logger log = LoggerFactory.getLogger(StudentCollectionReminderPlanner.class);

    private final CollectionReminderRepository reminderRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;

    public StudentCollectionReminderPlanner(CollectionReminderRepository reminderRepository, ApplicationEventPublisher eventPublisher, MeterRegistry meterRegistry) {
        this.reminderRepository = reminderRepository;
        this.eventPublisher = eventPublisher;
        this.meterRegistry = meterRegistry;
    }

    public StudentPlanningOutcome planReminderOfStudentOn(StudentReminderContext context, Map<Long, ResolvedReminderSchedule> schedulesByInvoiceId, LocalDate businessDate,
                                                          NonBusinessDays nonBusinessDays, ReminderDigestMode mode, ReminderContentTerms terms) {
        return planReminderOfStudentOn(context, schedulesByInvoiceId, businessDate, nonBusinessDays, mode, terms, MissedReminderRecovery.none());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StudentPlanningOutcome planReminderOfStudentOn(StudentReminderContext context, Map<Long, ResolvedReminderSchedule> schedulesByInvoiceId, LocalDate businessDate,
                                                          NonBusinessDays nonBusinessDays, ReminderDigestMode mode, ReminderContentTerms terms, MissedReminderRecovery recovery) {
        if (reminderRepository.existsByStudentIdAndBusinessDate(context.studentId(), businessDate)) {
            return StudentPlanningOutcome.ALREADY_PLANNED;
        }
        DailyReminderDigest digest = DailyReminderDigestPlanner.planDailyDigestOf(context.facts(), schedulesByInvoiceId, businessDate, nonBusinessDays, mode,
                recoveryExcludingWhatWasHandled(context.studentId(), recovery));
        if (digest.hasNothingToSend() && digest.suppressed().isEmpty()) {
            return StudentPlanningOutcome.NOTHING_TO_SEND;
        }
        List<PlannedReminderMessage> inAppMessages = CollectionReminderPlannedEventFactory.inAppMessagesOf(digest, terms);
        List<PlannedReminderMessage> emailMessages = CollectionReminderPlannedEventFactory.emailMessagesOf(digest, terms);
        CollectionReminder saved = reminderRepository.saveAndFlush(CollectionReminder.recordPlannedFor(context.studentId(), businessDate, inAppMessages, emailMessages,
                digest.suppressed()));
        if (!digest.hasNothingToSend()) {
            eventPublisher.publishEvent(CollectionReminderPlannedEventFactory.plannedEventOf(saved.getId(), context.personId(), digest, terms));
        }
        recordMetrics(inAppMessages, emailMessages, digest);
        log.info("Recordatorio de cobranza planificado: alumno={}, fecha={}, mensajesApp={}, mensajesMail={}, suprimidos={}", context.studentId(), businessDate,
                inAppMessages.size(), emailMessages.size(), digest.suppressed().size());
        return StudentPlanningOutcome.PLANNED;
    }

    private MissedReminderRecovery recoveryExcludingWhatWasHandled(Long studentId, MissedReminderRecovery recovery) {
        if (!recovery.isRecovering()) {
            return recovery;
        }
        Set<FiredReminderOccurrence> handled = new HashSet<>();
        for (CollectionReminder reminder : reminderRepository.findByStudentIdAndBusinessDateIn(studentId, recovery.missedDays())) {
            LocalDate day = reminder.getBusinessDate();
            reminder.getInAppMessages().forEach(message -> collectHandled(message, ReminderChannel.IN_APP, day, handled));
            reminder.getEmailMessages().forEach(message -> collectHandled(message, ReminderChannel.EMAIL, day, handled));
            reminder.getSuppressed().forEach(suppressed -> handled.add(new FiredReminderOccurrence(suppressed.invoiceId(), suppressed.noticeKey(), suppressed.channel(), day)));
        }
        return recovery.withAlreadyHandled(handled);
    }

    private static void collectHandled(PlannedReminderMessage message, ReminderChannel channel, LocalDate day, Set<FiredReminderOccurrence> handled) {
        for (PlannedReminderLine line : message.lines()) {
            for (PlannedReminderNotice notice : line.notices()) {
                handled.add(new FiredReminderOccurrence(line.invoiceId(), notice.noticeKey(), channel, day));
            }
        }
    }

    private void recordMetrics(List<PlannedReminderMessage> inAppMessages, List<PlannedReminderMessage> emailMessages, DailyReminderDigest digest) {
        inAppMessages.forEach(message -> meterRegistry.counter("billing.reminders.planned", "channel", "IN_APP", "format", message.format().name()).increment());
        emailMessages.forEach(message -> meterRegistry.counter("billing.reminders.planned", "channel", "EMAIL", "format", message.format().name()).increment());
        digest.suppressed().forEach(suppressed -> meterRegistry.counter("billing.reminders.suppressed", "reason", suppressed.reasonCode()).increment());
    }
}
