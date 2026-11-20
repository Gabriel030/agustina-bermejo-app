package com.red_escuela.backend.shared.retention;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public record RetentionSettings(
        boolean enabled,
        int loginAttemptMonths,
        int notificationMonths,
        int notificationDeliveryMonths,
        int collectionReminderDeliveryMonths,
        int batchSize,
        int maxBatchesPerRun) {

    public RetentionSettings(
            @Value("${app.retention.enabled:true}") boolean enabled,
            @Value("${app.retention.login-attempt-months:12}") int loginAttemptMonths,
            @Value("${app.retention.notification-months:12}") int notificationMonths,
            @Value("${app.retention.notification-delivery-months:12}") int notificationDeliveryMonths,
            @Value("${app.retention.collection-reminder-delivery-months:36}") int collectionReminderDeliveryMonths,
            @Value("${app.retention.batch-size:1000}") int batchSize,
            @Value("${app.retention.max-batches-per-run:1000}") int maxBatchesPerRun) {
        this.enabled = enabled;
        this.loginAttemptMonths = requirePositive("login-attempt-months", loginAttemptMonths);
        this.notificationMonths = requirePositive("notification-months", notificationMonths);
        this.notificationDeliveryMonths = requirePositive("notification-delivery-months", notificationDeliveryMonths);
        this.collectionReminderDeliveryMonths = requirePositive("collection-reminder-delivery-months", collectionReminderDeliveryMonths);
        this.batchSize = requirePositive("batch-size", batchSize);
        this.maxBatchesPerRun = requirePositive("max-batches-per-run", maxBatchesPerRun);
    }

    private static int requirePositive(String property, int value) {
        if (value < 1) {
            throw new IllegalArgumentException("app.retention." + property + " debe ser mayor o igual a 1");
        }
        return value;
    }
}
