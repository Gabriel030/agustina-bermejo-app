package com.red_escuela.backend.notification;

import com.red_escuela.backend.notification.entity.Notification;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InAppNotificationChannelTest {

    @Test
    void messageOfExactlyColumnLengthIsKeptUntouched() {
        String message = "a".repeat(Notification.MESSAGE_COLUMN_LENGTH);

        assertThat(InAppNotificationChannel.abbreviateToColumnLength(message)).isEqualTo(message);
    }

    @Test
    void messageOneCharacterOverColumnLengthIsTruncatedWithEllipsis() {
        String message = "a".repeat(Notification.MESSAGE_COLUMN_LENGTH + 1);

        String abbreviated = InAppNotificationChannel.abbreviateToColumnLength(message);

        assertThat(abbreviated).hasSize(Notification.MESSAGE_COLUMN_LENGTH).endsWith("…");
        assertThat(abbreviated).startsWith("a".repeat(Notification.MESSAGE_COLUMN_LENGTH - 1));
    }

    @Test
    void truncationNeverSplitsASurrogatePair() {
        String message = "a".repeat(Notification.MESSAGE_COLUMN_LENGTH - 2) + "😀" + "bbb";

        String abbreviated = InAppNotificationChannel.abbreviateToColumnLength(message);

        assertThat(abbreviated).hasSize(Notification.MESSAGE_COLUMN_LENGTH - 1).endsWith("a…");
    }
}
