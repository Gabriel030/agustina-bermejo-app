package com.red_escuela.backend.billing.collections.operation;

import com.red_escuela.backend.academicstructure.api.BusinessDayCalendar;
import com.red_escuela.backend.billing.collections.domain.NonBusinessDays;
import com.red_escuela.backend.billing.collections.domain.PostWithdrawalSummaryCandidate;
import com.red_escuela.backend.billing.collections.domain.PostWithdrawalSummarySchedule;
import com.red_escuela.backend.billing.collections.domain.WithdrawnEnrollmentDebtLine;
import com.red_escuela.backend.billing.collections.repository.PostWithdrawalSummaryStore;
import com.red_escuela.backend.billing.configuration.service.BillingConfigurationReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class PostWithdrawalSummaryPlanningStep {
    private static final Logger log = LoggerFactory.getLogger(PostWithdrawalSummaryPlanningStep.class);
    private static final int CALENDAR_DAYS_FROM_MONTH_START = 14;

    private final PostWithdrawalSummaryStore summaryStore;
    private final StudentPostWithdrawalSummaryRecorder summaryRecorder;
    private final BillingConfigurationReader configurationReader;
    private final BusinessDayCalendar businessDayCalendar;

    public PostWithdrawalSummaryPlanningStep(PostWithdrawalSummaryStore summaryStore, StudentPostWithdrawalSummaryRecorder summaryRecorder,
                                             BillingConfigurationReader configurationReader, BusinessDayCalendar businessDayCalendar) {
        this.summaryStore = summaryStore;
        this.summaryRecorder = summaryRecorder;
        this.configurationReader = configurationReader;
        this.businessDayCalendar = businessDayCalendar;
    }

    public int planPostWithdrawalSummariesOf(LocalDate businessDate) {
        NonBusinessDays nonBusinessDays = nonBusinessDaysFromStartOfMonthOf(businessDate);
        if (!businessDate.equals(PostWithdrawalSummarySchedule.summaryDayOfMonth(YearMonth.from(businessDate), nonBusinessDays))) {
            return 0;
        }
        int summaryMonths = configurationReader.collectionReminderSettingsEffectiveOn(businessDate).postWithdrawalSummaryMonths();
        List<WithdrawnEnrollmentDebtLine> debtLines = summaryStore.findPendingDebtLinesOfWithdrawnEnrollments(businessDate);
        int recorded = 0;
        for (PostWithdrawalSummaryCandidate candidate : PostWithdrawalSummaryCandidate.groupDueOn(debtLines, businessDate, summaryMonths, nonBusinessDays)) {
            try {
                summaryRecorder.recordSummaryOf(candidate, businessDate);
                recorded++;
            } catch (RuntimeException failure) {
                log.warn("Falló el resumen post-baja: alumno={}, fecha={}", candidate.studentId(), businessDate, failure);
            }
        }
        log.info("Resumen post-baja planificado: fecha={}, meses={}, alumnos={}", businessDate, summaryMonths, recorded);
        return recorded;
    }

    private NonBusinessDays nonBusinessDaysFromStartOfMonthOf(LocalDate businessDate) {
        Set<LocalDate> holidays = new HashSet<>();
        LocalDate firstDay = businessDate.withDayOfMonth(1);
        for (LocalDate day = firstDay; !day.isAfter(firstDay.plusDays(CALENDAR_DAYS_FROM_MONTH_START)); day = day.plusDays(1)) {
            if (!businessDayCalendar.isBusinessDay(day)) {
                holidays.add(day);
            }
        }
        return new NonBusinessDays(holidays);
    }
}
