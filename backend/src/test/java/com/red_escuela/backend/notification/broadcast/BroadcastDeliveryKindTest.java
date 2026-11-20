package com.red_escuela.backend.notification.broadcast;

import com.red_escuela.backend.notification.broadcast.domain.BroadcastCategory;
import com.red_escuela.backend.notification.broadcast.domain.BroadcastDeliveryKind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BroadcastDeliveryKindTest {

    @Test
    void kindWithoutBroadcastPrefixHasNoCategory() {
        assertThat(BroadcastDeliveryKind.categoryOf("TEST_TRANSACTIONAL")).isEmpty();
        assertThat(BroadcastDeliveryKind.categoryOf(null)).isEmpty();
    }

    @Test
    void broadcastKindRoundTripsItsCategory() {
        assertThat(BroadcastDeliveryKind.categoryOf(BroadcastDeliveryKind.of(BroadcastCategory.PROMOTIONAL)))
                .contains(BroadcastCategory.PROMOTIONAL);
    }
}
