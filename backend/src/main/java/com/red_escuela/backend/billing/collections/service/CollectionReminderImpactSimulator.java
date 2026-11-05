package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.academicstructure.api.BusinessDayCalendar;
import com.red_escuela.backend.billing.collections.domain.CollectionReminderImpact;
import com.red_escuela.backend.billing.collections.domain.CollectionReminderImpactQuery;
import com.red_escuela.backend.billing.collections.domain.NonBusinessDays;
import com.red_escuela.backend.billing.collections.domain.ReminderDigestMode;
import com.red_escuela.backend.billing.collections.domain.ReminderStudentFacts;
import com.red_escuela.backend.billing.collections.operation.CollectionReminderImpactCalculator;
import com.red_escuela.backend.billing.configuration.domain.CollectionReminderSettings;
import com.red_escuela.backend.billing.configuration.domain.ReminderScheduleProposal;
import com.red_escuela.backend.billing.configuration.domain.ReminderScheduleTarget;
import com.red_escuela.backend.billing.configuration.domain.ResolvedReminderSchedule;
import com.red_escuela.backend.billing.configuration.service.BillingConfigurationReader;
import com.red_escuela.backend.billing.configuration.service.ReminderScheduleTargets;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class CollectionReminderImpactSimulator {
    private static final int PAGE_SIZE = 200;
    private static final int SIMULATED_DAYS = 30;
    private static final int CALENDAR_MARGIN_DAYS = 14;

    private final CollectionReminderFactsLoader factsLoader;
    private final BillingConfigurationReader configurationReader;
    private final ReminderScheduleTargets scheduleTargets;
    private final BusinessDayCalendar businessDayCalendar;
    private final Clock clock;

    public CollectionReminderImpactSimulator(CollectionReminderFactsLoader factsLoader, BillingConfigurationReader configurationReader, ReminderScheduleTargets scheduleTargets,
                                              BusinessDayCalendar businessDayCalendar, Clock clock) {
        this.factsLoader = factsLoader;
        this.configurationReader = configurationReader;
        this.scheduleTargets = scheduleTargets;
        this.businessDayCalendar = businessDayCalendar;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CollectionReminderImpact simulateImpactOf(CollectionReminderImpactQuery query) {
        Optional<ReminderScheduleProposal> proposal = query.proposedSchedule();
        LocalDate effectiveFrom = query.effectiveFrom();
        proposal.ifPresentOrElse(scheduleTargets::requireValidProposal,
                () -> query.proposedGlobalSettings().ifPresent(scheduleTargets::requireValidGlobalSettings));
        LocalDate today = LocalDate.now(clock);
        LocalDate windowStart = effectiveFrom.isAfter(today) ? effectiveFrom : today.plusDays(1);
        LocalDate windowEnd = windowStart.plusDays(SIMULATED_DAYS);
        ReminderDigestMode currentMode = modeOf(configurationReader.collectionReminderSettingsEffectiveOn(windowStart));
        ReminderDigestMode proposedMode = query.proposedGlobalSettings().map(CollectionReminderImpactSimulator::modeOf).orElse(currentMode);
        NonBusinessDays nonBusinessDays = nonBusinessDaysBetween(windowStart.minusDays(CALENDAR_MARGIN_DAYS), windowEnd.plusDays(CALENDAR_MARGIN_DAYS));
        Map<ReminderScheduleTarget, ResolvedReminderSchedule> currentCache = new HashMap<>();
        Map<ReminderScheduleTarget, ResolvedReminderSchedule> proposedCache = new HashMap<>();
        int changed = 0;
        Long lastStudentId = 0L;
        while (true) {
            List<Long> page = factsLoader.findCandidateStudentIdsAfter(lastStudentId, PAGE_SIZE);
            if (page.isEmpty()) {
                break;
            }
            lastStudentId = page.get(page.size() - 1);
            List<ReminderStudentFacts> students = new ArrayList<>();
            Map<Long, ResolvedReminderSchedule> currentSchedules = new HashMap<>();
            Map<Long, ResolvedReminderSchedule> proposedSchedules = new HashMap<>();
            for (StudentReminderContext context : factsLoader.loadContextsOf(page, today)) {
                students.add(context.facts());
                currentSchedules.putAll(factsLoader.resolveSchedulesOf(context, windowStart, currentCache));
                if (proposal.isPresent()) {
                    context.scheduleTargetsByInvoiceId().forEach((invoiceId, target) -> proposedSchedules.put(invoiceId,
                            proposedCache.computeIfAbsent(target, key -> configurationReader.reminderScheduleWithProposal(key, windowStart, proposal.get()))));
                } else {
                    proposedSchedules.putAll(currentSchedules);
                }
            }
            changed += CollectionReminderImpactCalculator.countStudentsWithChangedSchedule(students, currentSchedules, proposedSchedules, currentMode, proposedMode,
                    windowStart, windowEnd, nonBusinessDays);
        }
        return new CollectionReminderImpact(changed);
    }

    private static ReminderDigestMode modeOf(CollectionReminderSettings settings) {
        return settings.dailyDigestEnabled() ? ReminderDigestMode.DAILY_DIGEST : ReminderDigestMode.SEPARATE_MESSAGES;
    }

    private NonBusinessDays nonBusinessDaysBetween(LocalDate from, LocalDate to) {
        Set<LocalDate> holidays = new HashSet<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            if (!businessDayCalendar.isBusinessDay(day)) {
                holidays.add(day);
            }
        }
        return new NonBusinessDays(holidays);
    }
}
