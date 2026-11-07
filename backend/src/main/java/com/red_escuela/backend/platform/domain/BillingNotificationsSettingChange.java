package com.red_escuela.backend.platform.domain;

public record BillingNotificationsSettingChange(boolean enabled, Long expectedVersion, String reason) {
}
