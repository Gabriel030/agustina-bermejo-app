package com.red_escuela.backend.identity.dto;

import com.red_escuela.backend.identity.api.InstitutionAcademicSettings;

public record AcademicSettingsResponse(int justificationDeadlineBusinessDays, int evaluationConfigDueDays, boolean announcementEmailEnabled) {

    public static AcademicSettingsResponse from(InstitutionAcademicSettings settings) {
        return new AcademicSettingsResponse(
                settings.justificationDeadlineBusinessDays(), settings.evaluationConfigDueDays(), settings.announcementEmailEnabled());
    }
}
