package com.red_escuela.backend.billing.collections.dto;

import com.red_escuela.backend.billing.collections.domain.CollectionReminderImpactQuery;
import com.red_escuela.backend.billing.configuration.domain.ReminderScheduleProposal;
import com.red_escuela.backend.billing.configuration.domain.ReminderScheduleTarget;
import com.red_escuela.backend.billing.configuration.domain.TeachingGroup;
import com.red_escuela.backend.billing.configuration.dto.CollectionReminderSettingsValues;
import com.red_escuela.backend.billing.configuration.dto.ReminderScheduleDto;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CollectionReminderImpactRequest(@Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Grupo de la agenda propuesta; va junto con schedule. Sin ninguno de los dos, el cambio es solo de ajustes globales")
                                              TeachingGroup group,
                                              @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Programa o curso de la excepción; sin valor, el cambio es a la política del grupo")
                                              Long targetId,
                                              @Valid @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Agenda propuesta; va junto con group")
                                              ReminderScheduleDto schedule, @NotNull LocalDate effectiveFrom,
                                              @Valid @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Ajustes globales propuestos; si no vienen, quedan los vigentes. Obligatorios cuando no hay agenda")
                                              CollectionReminderSettingsValues globalSettings) {

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "group y schedule van juntos: se envían los dos o ninguno")
    public boolean isGroupAndScheduleSentTogether() {
        return (schedule != null) == (group != null);
    }

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "Sin agenda propuesta, el impacto necesita ajustes globales y no admite targetId")
    public boolean isGlobalSettingsOnlyChangeComplete() {
        return schedule != null || (targetId == null && globalSettings != null);
    }

    public CollectionReminderImpactQuery toQuery() {
        if (schedule == null) {
            return CollectionReminderImpactQuery.ofGlobalSettings(globalSettings.toSettings(), effectiveFrom);
        }
        return new CollectionReminderImpactQuery(new ReminderScheduleProposal(new ReminderScheduleTarget(group, targetId), schedule.toSchedule(),
                globalSettings == null ? null : globalSettings.toSettings()), effectiveFrom);
    }
}
