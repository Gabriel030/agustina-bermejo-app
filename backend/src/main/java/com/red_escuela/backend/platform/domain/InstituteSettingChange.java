package com.red_escuela.backend.platform.domain;

import com.red_escuela.backend.platform.model.InstituteSettingCode;

public record InstituteSettingChange(InstituteSettingCode code, boolean enabled, Long expectedVersion, String reason) {
}
