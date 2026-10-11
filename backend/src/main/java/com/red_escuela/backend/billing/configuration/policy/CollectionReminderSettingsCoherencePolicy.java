package com.red_escuela.backend.billing.configuration.policy;

import com.red_escuela.backend.billing.configuration.domain.CollectionReminderSettings;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "INVALID_COLLECTION_REMINDER_SETTINGS", decisions = {"D-351", "D-359"})
public final class CollectionReminderSettingsCoherencePolicy implements Policy<CollectionReminderSettings> {
    private static final String CODE = "INVALID_COLLECTION_REMINDER_SETTINGS";
    private static final int MIN_DAYS = 1;
    private static final int MAX_DAYS = 60;

    public String ruleName() {
        return "CollectionReminderSettingsCoherencePolicy";
    }

    public PolicyVerdict evaluateRule(CollectionReminderSettings settings) {
        String violation = firstViolationOf(settings);
        return violation == null ? PolicyVerdict.allow(ruleName()) : PolicyVerdict.deny(ruleName(), CODE, violation);
    }

    private String firstViolationOf(CollectionReminderSettings settings) {
        if (!withinDayRange(settings.daysAfterIssueReminder())) {
            return "daysAfterIssueReminder debe estar entre 1 y 60.";
        }
        if (!withinDayRange(settings.daysBeforeDueReminder())) {
            return "daysBeforeDueReminder debe estar entre 1 y 60.";
        }
        if (!withinDayRange(settings.daysAfterDueReminder())) {
            return "daysAfterDueReminder debe estar entre 1 y 60.";
        }
        if (!withinDayRange(settings.daysBeforeSuspensionReminder())) {
            return "daysBeforeSuspensionReminder debe estar entre 1 y 60.";
        }
        if (!settings.emailWindowStart().isBefore(settings.emailWindowEnd())) {
            return "La franja de envío debe empezar antes de terminar.";
        }
        if (settings.dailyEmailCap() < 1) {
            return "El tope diario de mails debe ser al menos 1.";
        }
        if (settings.minDaysBetweenEmails() < 1) {
            return "El espaciado mínimo entre mails debe ser al menos 1 día.";
        }
        if (settings.maxReminderEmailsPerMonth() < 1) {
            return "El máximo mensual de mails por alumno debe ser al menos 1.";
        }
        if (settings.postWithdrawalSummaryMonths() < 0) {
            return "Los meses de resumen posterior a la baja no pueden ser negativos.";
        }
        if (settings.contactListDaysOverdue() < 1 || settings.contactListOverdueInstallments() < 1) {
            return "Los umbrales de la lista Para contactar deben ser al menos 1.";
        }
        return null;
    }

    private boolean withinDayRange(int days) {
        return days >= MIN_DAYS && days <= MAX_DAYS;
    }
}
