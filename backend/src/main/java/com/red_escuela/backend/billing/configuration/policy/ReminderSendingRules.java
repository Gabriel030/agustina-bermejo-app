package com.red_escuela.backend.billing.configuration.policy;

import com.red_escuela.backend.billing.configuration.domain.ReminderSendingOverrides;

public final class ReminderSendingRules {
    private static final int MIN_POST_WITHDRAWAL_MONTHS = 1;
    private static final int MAX_POST_WITHDRAWAL_MONTHS = 24;

    private ReminderSendingRules() {
    }

    public static String firstViolationOf(ReminderSendingOverrides settings) {
        if ((settings.emailWindowStart() == null) != (settings.emailWindowEnd() == null)) {
            return "La franja de envío se define con inicio y fin juntos.";
        }
        if (settings.emailWindowStart() != null && !settings.emailWindowStart().isBefore(settings.emailWindowEnd())) {
            return "La franja de envío debe empezar antes de terminar.";
        }
        if (settings.dailyEmailCap() != null && settings.dailyEmailCap() < 1) {
            return "El tope diario de mails debe ser al menos 1.";
        }
        if (settings.minDaysBetweenEmails() != null && settings.minDaysBetweenEmails() < 1) {
            return "El espaciado mínimo entre mails debe ser al menos 1 día.";
        }
        if (settings.maxReminderEmailsPerMonth() != null && settings.maxReminderEmailsPerMonth() < 1) {
            return "El máximo mensual de mails por alumno debe ser al menos 1.";
        }
        if (settings.postWithdrawalSummaryMonths() != null
                && (settings.postWithdrawalSummaryMonths() < MIN_POST_WITHDRAWAL_MONTHS || settings.postWithdrawalSummaryMonths() > MAX_POST_WITHDRAWAL_MONTHS)) {
            return "Los meses de resumen posterior a la baja deben estar entre 1 y 24.";
        }
        if ((settings.contactListDaysOverdue() != null && settings.contactListDaysOverdue() < 1)
                || (settings.contactListOverdueInstallments() != null && settings.contactListOverdueInstallments() < 1)) {
            return "Los umbrales de la lista Para contactar deben ser al menos 1.";
        }
        return null;
    }
}
