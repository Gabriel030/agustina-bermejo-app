package com.red_escuela.backend.identity.dto;

import java.util.Map;

public record NotificationPreferencesResponse(Map<String, Map<String, Boolean>> preferences) {
}
