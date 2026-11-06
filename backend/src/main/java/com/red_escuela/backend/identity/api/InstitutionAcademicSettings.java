package com.red_escuela.backend.identity.api;

public record InstitutionAcademicSettings(int justificationDeadlineBusinessDays, int evaluationConfigDueDays, boolean announcementEmailEnabled) {
    public static final InstitutionAcademicSettings DEFAULTS = new InstitutionAcademicSettings(5, 15, false);
}
