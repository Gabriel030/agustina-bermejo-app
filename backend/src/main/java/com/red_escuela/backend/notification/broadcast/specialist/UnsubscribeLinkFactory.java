package com.red_escuela.backend.notification.broadcast.specialist;

import com.red_escuela.backend.notification.broadcast.domain.BroadcastCategory;
import com.red_escuela.backend.notification.broadcast.domain.BroadcastDeliveryKind;
import com.red_escuela.backend.notification.broadcast.domain.ConsentChannel;
import com.red_escuela.backend.notification.delivery.domain.DeliveryCategory;
import com.red_escuela.backend.notification.delivery.specialist.DeliveryUnsubscribeLinkProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Component
public class UnsubscribeLinkFactory implements DeliveryUnsubscribeLinkProvider {

    static final String UNSUBSCRIBE_PATH = "/baja?token=";

    private final UnsubscribeTokenIssuer tokenIssuer;
    private final String frontendBaseUrl;

    public UnsubscribeLinkFactory(UnsubscribeTokenIssuer tokenIssuer, @Value("${app.frontend.base-url}") String frontendBaseUrl) {
        this.tokenIssuer = tokenIssuer;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    public String linkFor(Long personId, ConsentChannel channel, BroadcastCategory category) {
        return frontendBaseUrl + UNSUBSCRIBE_PATH + URLEncoder.encode(tokenIssuer.issueToken(personId, channel, category), StandardCharsets.UTF_8);
    }

    @Override
    public Optional<String> unsubscribeUrlFor(DeliveryCategory category, String kind, Long recipientPersonId) {
        if (category != DeliveryCategory.BROADCAST) {
            return Optional.empty();
        }
        return BroadcastDeliveryKind.categoryOf(kind).map(broadcastCategory -> linkFor(recipientPersonId, ConsentChannel.EMAIL, broadcastCategory));
    }
}
