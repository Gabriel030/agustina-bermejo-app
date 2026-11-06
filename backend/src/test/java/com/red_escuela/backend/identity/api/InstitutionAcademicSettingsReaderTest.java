package com.red_escuela.backend.identity.api;

import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.repository.InstitutionRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InstitutionAcademicSettingsReaderTest {

    private final InstitutionRepository repository = mock(InstitutionRepository.class);
    private final InstitutionAcademicSettingsReader reader = new InstitutionAcademicSettingsReader(repository);

    @Test
    void readReturnsDefaultsWhenNoInstitutionExists() {
        when(repository.findAll()).thenReturn(List.of());

        assertThat(reader.read()).isEqualTo(InstitutionAcademicSettings.DEFAULTS);
        assertThat(InstitutionAcademicSettings.DEFAULTS)
                .isEqualTo(new InstitutionAcademicSettings(5, 15));
    }

    @Test
    void readReturnsStoredValues() {
        Institution institution = new Institution();
        institution.setJustificationDeadlineBusinessDays(3);
        institution.setEvaluationConfigDueDays(30);
        when(repository.findAll()).thenReturn(List.of(institution));

        assertThat(reader.read()).isEqualTo(new InstitutionAcademicSettings(3, 30));
    }

    @Test
    void aNewInstitutionStartsWithTheDefaultSettings() {
        Institution institution = new Institution();

        assertThat(new InstitutionAcademicSettings(
                institution.getJustificationDeadlineBusinessDays(), institution.getEvaluationConfigDueDays()))
                .isEqualTo(InstitutionAcademicSettings.DEFAULTS);
    }
}
