package com.red_escuela.backend.billing.collections.operation;

import com.red_escuela.backend.academicstructure.api.BusinessDayCalendar;
import com.red_escuela.backend.billing.collections.domain.NonBusinessDays;
import com.red_escuela.backend.billing.collections.domain.ReminderDigestMode;
import com.red_escuela.backend.billing.collections.service.CollectionReminderFactsLoader;
import com.red_escuela.backend.billing.collections.service.StudentReminderContext;
import com.red_escuela.backend.billing.configuration.domain.CollectionReminderSettings;
import com.red_escuela.backend.billing.configuration.domain.ReminderScheduleTarget;
import com.red_escuela.backend.billing.configuration.domain.ResolvedReminderSchedule;
import com.red_escuela.backend.billing.configuration.service.BillingConfigurationReader;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class CollectionReminderPlanningStep {
    private static final Logger log = LoggerFactory.getLogger(CollectionReminderPlanningStep.class);
    private static final String STUDENT_DAY_CONSTRAINT = "uq_collection_reminder_student_day";
    private static final int PAGE_SIZE = 200;
    private static final int CALENDAR_DAYS_BEFORE = 14;
    private static final int CALENDAR_DAYS_AFTER = 30;

    private final CollectionReminderFactsLoader factsLoader;
    private final StudentCollectionReminderPlanner studentPlanner;
    private final BillingConfigurationReader configurationReader;
    private final BusinessDayCalendar businessDayCalendar;

    public CollectionReminderPlanningStep(CollectionReminderFactsLoader factsLoader, StudentCollectionReminderPlanner studentPlanner,
                                          BillingConfigurationReader configurationReader, BusinessDayCalendar businessDayCalendar) {
        this.factsLoader = factsLoader;
        this.studentPlanner = studentPlanner;
        this.configurationReader = configurationReader;
        this.businessDayCalendar = businessDayCalendar;
    }

    public CollectionReminderPlanningResult planCollectionReminders(LocalDate businessDate) {
        PlanningRun run = startRun(businessDate);
        Long lastStudentId = 0L;
        while (true) {
            List<Long> page = factsLoader.findCandidateStudentIdsAfter(lastStudentId, PAGE_SIZE);
            if (page.isEmpty()) {
                break;
            }
            planPage(page, run);
            lastStudentId = page.get(page.size() - 1);
        }
        return run.result();
    }

    public CollectionReminderPlanningResult planCollectionRemindersOf(LocalDate businessDate, Collection<Long> studentIds) {
        PlanningRun run = startRun(businessDate);
        List<Long> candidates = factsLoader.findCandidateStudentIdsAmong(studentIds);
        if (!candidates.isEmpty()) {
            planPage(candidates, run);
        }
        return run.result();
    }

    private PlanningRun startRun(LocalDate businessDate) {
        CollectionReminderSettings settings = configurationReader.collectionReminderSettingsEffectiveOn(businessDate);
        ReminderDigestMode mode = settings.dailyDigestEnabled() ? ReminderDigestMode.DAILY_DIGEST : ReminderDigestMode.SEPARATE_MESSAGES;
        return new PlanningRun(businessDate, nonBusinessDaysAround(businessDate), mode);
    }

    private NonBusinessDays nonBusinessDaysAround(LocalDate businessDate) {
        Set<LocalDate> holidays = new HashSet<>();
        for (LocalDate day = businessDate.minusDays(CALENDAR_DAYS_BEFORE); !day.isAfter(businessDate.plusDays(CALENDAR_DAYS_AFTER)); day = day.plusDays(1)) {
            if (!businessDayCalendar.isBusinessDay(day)) {
                holidays.add(day);
            }
        }
        return new NonBusinessDays(holidays);
    }

    private void planPage(List<Long> studentIds, PlanningRun run) {
        List<StudentReminderContext> contexts = factsLoader.loadContextsOf(studentIds, run.businessDate);
        run.candidates += studentIds.size();
        for (StudentReminderContext context : contexts) {
            planStudent(context, run);
        }
        Set<Long> loaded = new HashSet<>();
        contexts.forEach(context -> loaded.add(context.studentId()));
        for (Long studentId : studentIds) {
            if (!loaded.contains(studentId)) {
                run.failed++;
                log.warn("Alumno candidato sin hechos cargados: alumno={}, fecha={}", studentId, run.businessDate);
                run.errors.add(new PlanningError(studentId, "No se pudieron cargar los hechos del alumno"));
            }
        }
    }

    private void planStudent(StudentReminderContext context, PlanningRun run) {
        try {
            Map<Long, ResolvedReminderSchedule> schedules = factsLoader.resolveSchedulesOf(context, run.businessDate, run.scheduleCache);
            StudentPlanningOutcome outcome = studentPlanner.planReminderOfStudentOn(context, schedules, run.businessDate, run.nonBusinessDays, run.mode);
            switch (outcome) {
                case PLANNED -> run.planned++;
                case ALREADY_PLANNED -> run.alreadyPlanned++;
                case NOTHING_TO_SEND -> run.nothingToSend++;
            }
        } catch (DataIntegrityViolationException violation) {
            if (isStudentDayDuplicate(violation)) {
                run.alreadyPlanned++;
                log.info("Recordatorio de cobranza ya planificado: alumno={}, fecha={}", context.studentId(), run.businessDate);
            } else {
                recordFailure(context, run, violation);
            }
        } catch (Exception e) {
            recordFailure(context, run, e);
        }
    }

    private void recordFailure(StudentReminderContext context, PlanningRun run, Exception failure) {
        run.failed++;
        log.warn("Falló la planificación del recordatorio de cobranza: alumno={}, fecha={}", context.studentId(), run.businessDate, failure);
        run.errors.add(new PlanningError(context.studentId(), failure.getMessage()));
    }

    private static boolean isStudentDayDuplicate(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation && STUDENT_DAY_CONSTRAINT.equalsIgnoreCase(violation.getConstraintName())) {
                return true;
            }
        }
        return false;
    }

    private static final class PlanningRun {
        private final LocalDate businessDate;
        private final NonBusinessDays nonBusinessDays;
        private final ReminderDigestMode mode;
        private final Map<ReminderScheduleTarget, ResolvedReminderSchedule> scheduleCache = new HashMap<>();
        private final List<PlanningError> errors = new ArrayList<>();
        private int candidates;
        private int planned;
        private int alreadyPlanned;
        private int nothingToSend;
        private int failed;

        private PlanningRun(LocalDate businessDate, NonBusinessDays nonBusinessDays, ReminderDigestMode mode) {
            this.businessDate = businessDate;
            this.nonBusinessDays = nonBusinessDays;
            this.mode = mode;
        }

        private CollectionReminderPlanningResult result() {
            return new CollectionReminderPlanningResult(candidates, planned, alreadyPlanned, nothingToSend, failed, errors);
        }
    }
}
