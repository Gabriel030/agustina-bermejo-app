package com.red_escuela.backend.notification.delivery.service;

import com.red_escuela.backend.notification.delivery.domain.DeliveryChannel;
import com.red_escuela.backend.notification.delivery.repository.NotificationDeliveryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class NotificationDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(NotificationDeliveryService.class);

    private final InAppDeliveryChannel inAppChannel;
    private final EmailDeliveryChannel emailChannel;
    private final NotificationDeliveryRepository deliveryRepository;

    public NotificationDeliveryService(InAppDeliveryChannel inAppChannel, EmailDeliveryChannel emailChannel,
                                       NotificationDeliveryRepository deliveryRepository) {
        this.inAppChannel = inAppChannel;
        this.emailChannel = emailChannel;
        this.deliveryRepository = deliveryRepository;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void enqueue(DeliveryRequest request) {
        if (request.sendsInApp()) {
            enqueueOnce(request, DeliveryChannel.IN_APP, () -> inAppChannel.deliver(request));
        }
        if (request.sendsEmail()) {
            enqueueOnce(request, DeliveryChannel.EMAIL, () -> emailChannel.enqueue(request));
        }
    }

    private void enqueueOnce(DeliveryRequest request, DeliveryChannel channel, Runnable channelWork) {
        try {
            channelWork.run();
        } catch (DataIntegrityViolationException duplicate) {
            String dedupKey = request.envelope().dedupKey();
            if (!deliveryRepository.existsByDedupKeyAndChannel(dedupKey, channel)) {
                throw duplicate;
            }
            log.info("Entrega ya registrada dedup={} canal={}", dedupKey, channel);
        }
    }
}
