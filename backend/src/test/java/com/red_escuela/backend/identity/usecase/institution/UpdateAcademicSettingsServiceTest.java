package com.red_escuela.backend.identity.usecase.institution;

import com.red_escuela.backend.identity.dto.AcademicSettingsRequest;
import com.red_escuela.backend.identity.dto.AcademicSettingsResponse;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.exception.InstitutionNotFoundException;
import com.red_escuela.backend.identity.repository.InstitutionRepository;
import com.red_escuela.backend.identity.specialist.InstitutionAcademicSettingsWriter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UpdateAcademicSettingsServiceTest {

    private final InstitutionRepository repository = mock(InstitutionRepository.class);
    private final UpdateAcademicSettingsService service =
            new UpdateAcademicSettingsService(new InstitutionAcademicSettingsWriter(repository));

    @Test
    void updatePersistsBothValues() {
        Institution institution = new Institution();
        when(repository.findAll()).thenReturn(List.of(institution));

        AcademicSettingsResponse response = service.update(new AcademicSettingsRequest(7, 40, true));

        assertThat(response).isEqualTo(new AcademicSettingsResponse(7, 40, true));
        assertThat(institution.isAnnouncementEmailEnabled()).isTrue();
        assertThat(institution.getJustificationDeadlineBusinessDays()).isEqualTo(7);
        assertThat(institution.getEvaluationConfigDueDays()).isEqualTo(40);
        verify(repository).save(institution);
    }

    @Test
    void updateFailsWhenThereIsNoInstitution() {
        when(repository.findAll()).thenReturn(List.of());

        assertThatThrownBy(() -> service.update(new AcademicSettingsRequest(7, 40, null)))
                .isInstanceOf(InstitutionNotFoundException.class);
    }

    @Test
    void updateWithoutTheEmailSwitchKeepsTheStoredValue() {
        Institution institution = new Institution();
        institution.setAnnouncementEmailEnabled(true);
        when(repository.findAll()).thenReturn(List.of(institution));

        AcademicSettingsResponse response = service.update(new AcademicSettingsRequest(7, 40, null));

        assertThat(response.announcementEmailEnabled()).isTrue();
        assertThat(institution.isAnnouncementEmailEnabled()).isTrue();
    }
}
