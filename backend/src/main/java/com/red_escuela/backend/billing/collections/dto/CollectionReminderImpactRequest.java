package com.red_escuela.backend.billing.collections.dto;

import com.red_escuela.backend.billing.collections.domain.CollectionReminderImpactQuery;
import com.red_escuela.backend.billing.configuration.domain.ReminderScheduleProposal;
import com.red_escuela.backend.billing.configuration.domain.ReminderScheduleTarget;
import com.red_escuela.backend.billing.configuration.domain.TeachingGroup;
import com.red_escuela.backend.billing.configuration.dto.CollectionReminderSettingsValues;
import com.red_escuela.backend.billing.configuration.dto.ReminderScheduleDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CollectionReminderImpactRequest(@NotNull TeachingGroup group,
                                              @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Programa o curso de la excepción; sin valor, el cambio es a la política del grupo")
                                              Long targetId,
                                              @NotNull @Valid ReminderScheduleDto schedule, @NotNull LocalDate effectiveFrom,
                                              @Valid @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Ajustes globales propuestos; si no vienen, quedan los vigentes")
                                              CollectionReminderSettingsValues globalSettings) {

    public CollectionReminderImpactQuery toQuery() {
        return new CollectionReminderImpactQuery(new ReminderScheduleProposal(new ReminderScheduleTarget(group, targetId), schedule.toSchedule(),
                globalSettings == null ? null : globalSettings.toSettings()), effectiveFrom);
    }
}
