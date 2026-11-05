package com.red_escuela.backend.identity.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

public record NotificationPreferencesResponse(Map<String, Map<String, Boolean>> preferences,
                                              @Schema(description = "Por evento, los canales que no se pueden apagar; siempre figuran activos en preferences")
                                              Map<String, List<String>> mandatoryChannels) {
}
