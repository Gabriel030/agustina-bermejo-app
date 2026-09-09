package com.red_escuela.backend.identity.dto;

import java.util.Map;

public record UpdateNotificationPreferencesRequest(Map<String, Map<String, Boolean>> preferences) {
}
