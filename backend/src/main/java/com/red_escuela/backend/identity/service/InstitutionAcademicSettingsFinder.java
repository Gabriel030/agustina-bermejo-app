package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.identity.api.InstitutionAcademicSettings;
import com.red_escuela.backend.identity.api.InstitutionAcademicSettingsReader;
import org.springframework.stereotype.Service;

@Service
public class InstitutionAcademicSettingsFinder {

    private final InstitutionAcademicSettingsReader reader;

    public InstitutionAcademicSettingsFinder(InstitutionAcademicSettingsReader reader) {
        this.reader = reader;
    }

    public InstitutionAcademicSettings find() {
        return reader.read();
    }
}
