package com.red_escuela.backend.identity.usecase.institution;

import com.red_escuela.backend.identity.dto.AcademicSettingsRequest;
import com.red_escuela.backend.identity.dto.AcademicSettingsResponse;
import com.red_escuela.backend.identity.service.InstitutionAcademicSettingsWriter;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class UpdateAcademicSettingsService implements UpdateAcademicSettingsUseCase {

    private final InstitutionAcademicSettingsWriter writer;

    public UpdateAcademicSettingsService(InstitutionAcademicSettingsWriter writer) {
        this.writer = writer;
    }

    @Override
    public AcademicSettingsResponse update(AcademicSettingsRequest request) {
        return AcademicSettingsResponse.from(writer.write(
                request.justificationDeadlineBusinessDays(), request.evaluationConfigDueDays()));
    }
}
