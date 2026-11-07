package com.red_escuela.backend.platform.domain;

import com.red_escuela.backend.platform.model.InstituteSettingCode;

import java.time.LocalDateTime;

public record InstituteSettingChangeEntry(Long id, InstituteSettingCode code, Boolean before, boolean after, ActorSummary actor, String reason, LocalDateTime changedAt) {
}
