package com.red_escuela.backend.platform.dto;

import com.red_escuela.backend.platform.domain.InstituteSettingChangeEntry;
import com.red_escuela.backend.platform.model.InstituteSettingCode;

import java.time.LocalDateTime;

public record InstituteSettingChangeResponse(Long id, InstituteSettingCode code, Boolean before, boolean after, ActorSummaryResponse actor, String reason, LocalDateTime changedAt) {

    public static InstituteSettingChangeResponse of(InstituteSettingChangeEntry entry) {
        return new InstituteSettingChangeResponse(entry.id(), entry.code(), entry.before(), entry.after(), ActorSummaryResponse.of(entry.actor()), entry.reason(), entry.changedAt());
    }
}
