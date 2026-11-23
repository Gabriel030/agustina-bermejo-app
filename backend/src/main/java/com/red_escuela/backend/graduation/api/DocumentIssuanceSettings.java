package com.red_escuela.backend.graduation.api;

import com.red_escuela.backend.graduation.documentsettings.dto.DocumentIssuanceSettingsResponse;
import com.red_escuela.backend.graduation.documentsettings.specialist.DocumentIssuanceSettingsFinder;
import org.springframework.stereotype.Service;

@Service
public class DocumentIssuanceSettings {

    private final DocumentIssuanceSettingsFinder finder;

    public DocumentIssuanceSettings(DocumentIssuanceSettingsFinder finder) {
        this.finder = finder;
    }

    public DocumentIssuanceValues current() {
        DocumentIssuanceSettingsResponse current = finder.current();
        return new DocumentIssuanceValues(current.examRegistrationAdvanceDays(), current.examAttendanceValidityDays(),
                current.approvedSubjectsValidityDays(), current.dailyIssuanceLimitPerType());
    }

    public record DocumentIssuanceValues(int examRegistrationAdvanceDays, int examAttendanceValidityDays,
                                         int approvedSubjectsValidityDays, int dailyIssuanceLimitPerType) {
    }
}
