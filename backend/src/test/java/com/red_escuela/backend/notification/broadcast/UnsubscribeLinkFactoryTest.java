package com.red_escuela.backend.notification.broadcast;

import com.red_escuela.backend.notification.broadcast.domain.BroadcastCategory;
import com.red_escuela.backend.notification.broadcast.domain.BroadcastDeliveryKind;
import com.red_escuela.backend.notification.broadcast.specialist.UnsubscribeLinkFactory;
import com.red_escuela.backend.notification.broadcast.specialist.UnsubscribeTokenIssuer;
import com.red_escuela.backend.notification.delivery.domain.DeliveryCategory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UnsubscribeLinkFactoryTest {

    private final UnsubscribeTokenIssuer issuer = mock(UnsubscribeTokenIssuer.class);
    private final UnsubscribeLinkFactory factory = new UnsubscribeLinkFactory(issuer, "https://front.example");

    @Test
    void kindWithoutBroadcastPrefixGetsNoUnsubscribeUrl() {
        assertThat(factory.unsubscribeUrlFor(DeliveryCategory.BROADCAST, "TEST_TRANSACTIONAL", 7L)).isEmpty();
    }

    @Test
    void nonBroadcastCategoryGetsNoUnsubscribeUrl() {
        assertThat(factory.unsubscribeUrlFor(DeliveryCategory.ANNOUNCEMENT, BroadcastDeliveryKind.of(BroadcastCategory.PROMOTIONAL), 7L)).isEmpty();
    }

    @Test
    void broadcastKindGetsTheSignedLink() {
        when(issuer.issueToken(any(), any(), any())).thenReturn("signed-token");

        assertThat(factory.unsubscribeUrlFor(DeliveryCategory.BROADCAST, BroadcastDeliveryKind.of(BroadcastCategory.PROMOTIONAL), 7L))
                .contains("https://front.example/baja?token=signed-token");
    }
}
