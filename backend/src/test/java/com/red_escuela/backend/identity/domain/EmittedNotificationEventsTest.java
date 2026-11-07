package com.red_escuela.backend.identity.domain;

import com.red_escuela.backend.identity.entity.NotificationEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmittedNotificationEventsTest {

    @Test
    void hidesEventsWithoutEmitter() {
        assertThat(EmittedNotificationEvents.visible())
                .doesNotContain(NotificationEvent.ACCOUNT_CREATED, NotificationEvent.INVOICE_GENERATED);
    }

    @Test
    void showsGradePostedNowThatItIsEmitted() {
        assertThat(EmittedNotificationEvents.visible()).contains(NotificationEvent.GRADE_POSTED);
    }

    @Test
    void keepsEventsThatHaveAnEmitter() {
        assertThat(EmittedNotificationEvents.visible())
                .contains(NotificationEvent.EXAM_BOARD_REGISTERED, NotificationEvent.PAYMENT_DUE_SOON,
                        NotificationEvent.HOLIDAY_CALENDAR_REMINDER, NotificationEvent.INSTITUTIONAL_NOTICE,
                        NotificationEvent.SUBJECT_ENROLLMENT);
    }

    @Test
    void visibleAndUnemittedPartitionEveryEvent() {
        assertThat(EmittedNotificationEvents.visible().size() + EmittedNotificationEvents.unemitted().size())
                .isEqualTo(NotificationEvent.values().length);
    }
}
