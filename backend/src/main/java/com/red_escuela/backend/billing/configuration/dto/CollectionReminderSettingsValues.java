package com.red_escuela.backend.billing.configuration.dto;

import com.red_escuela.backend.billing.configuration.domain.CollectionReminderSettings;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

public record CollectionReminderSettingsValues(@NotNull Boolean suspensionNoticeEnabled, @NotNull Integer postWithdrawalSummaryMonths,
                                               @NotNull LocalTime emailWindowStart, @NotNull LocalTime emailWindowEnd, @NotNull Integer dailyEmailCap,
                                               @NotNull Integer contactListDaysOverdue, @NotNull Integer contactListOverdueInstallments,
                                               @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Resumen diario único por alumno; si no viene, queda activado")
                                               Boolean dailyDigestEnabled) {

    public CollectionReminderSettings toSettings() {
        return new CollectionReminderSettings(suspensionNoticeEnabled, postWithdrawalSummaryMonths,
                emailWindowStart, emailWindowEnd, dailyEmailCap, contactListDaysOverdue, contactListOverdueInstallments,
                dailyDigestEnabled == null || dailyDigestEnabled);
    }

    public static CollectionReminderSettingsValues from(CollectionReminderSettings settings) {
        return new CollectionReminderSettingsValues(settings.suspensionNoticeEnabled(), settings.postWithdrawalSummaryMonths(),
                settings.emailWindowStart(), settings.emailWindowEnd(),
                settings.dailyEmailCap(), settings.contactListDaysOverdue(), settings.contactListOverdueInstallments(), settings.dailyDigestEnabled());
    }
}
