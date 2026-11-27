package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.identity.api.InstitutionAcademicSettingsReader;
import org.springframework.stereotype.Service;

@Service
public class SectionEvaluationConfigDueDaysReader {

    private final InstitutionAcademicSettingsReader academicSettingsReader;

    public SectionEvaluationConfigDueDaysReader(InstitutionAcademicSettingsReader academicSettingsReader) {
        this.academicSettingsReader = academicSettingsReader;
    }

    public int read() {
        return academicSettingsReader.read().evaluationConfigDueDays();
    }
}
