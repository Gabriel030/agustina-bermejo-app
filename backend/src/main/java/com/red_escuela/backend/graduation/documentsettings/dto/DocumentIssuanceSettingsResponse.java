package com.red_escuela.backend.graduation.documentsettings.dto;

public record DocumentIssuanceSettingsResponse(int examRegistrationAdvanceDays, int examAttendanceValidityDays, int approvedSubjectsValidityDays, int dailyIssuanceLimitPerType, long version) {
}
