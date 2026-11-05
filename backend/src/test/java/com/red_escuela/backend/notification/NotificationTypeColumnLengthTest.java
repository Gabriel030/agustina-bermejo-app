package com.red_escuela.backend.notification;

import com.red_escuela.backend.notification.entity.NotificationType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTypeColumnLengthTest {

    private static final int TYPE_COLUMN_LENGTH = 40;

    @Test
    void everyNotificationTypeFitsTheTypeColumn() {
        assertThat(Arrays.stream(NotificationType.values()).map(Enum::name).filter(name -> name.length() > TYPE_COLUMN_LENGTH)).isEmpty();
    }
}
