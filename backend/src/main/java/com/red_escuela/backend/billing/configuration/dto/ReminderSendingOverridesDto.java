package com.red_escuela.backend.billing.configuration.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import com.red_escuela.backend.billing.configuration.domain.ReminderSendingOverrides;

import java.time.LocalTime;

public record ReminderSendingOverridesDto(@Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) Boolean suspensionNoticeEnabled, @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) Integer postWithdrawalSummaryMonths,
                                          @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) LocalTime emailWindowStart, @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) LocalTime emailWindowEnd, @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) Integer dailyEmailCap,
                                          @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) Integer contactListDaysOverdue, @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) Integer contactListOverdueInstallments) {

    public ReminderSendingOverrides toOverrides() {
        return new ReminderSendingOverrides(suspensionNoticeEnabled, postWithdrawalSummaryMonths,
                emailWindowStart, emailWindowEnd, dailyEmailCap, contactListDaysOverdue, contactListOverdueInstallments);
    }

    public static ReminderSendingOverridesDto from(ReminderSendingOverrides overrides) {
        return new ReminderSendingOverridesDto(overrides.suspensionNoticeEnabled(), overrides.postWithdrawalSummaryMonths(),
                overrides.emailWindowStart(), overrides.emailWindowEnd(), overrides.dailyEmailCap(),
                overrides.contactListDaysOverdue(), overrides.contactListOverdueInstallments());
    }
}
