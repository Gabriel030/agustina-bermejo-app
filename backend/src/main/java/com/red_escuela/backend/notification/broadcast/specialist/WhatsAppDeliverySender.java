package com.red_escuela.backend.notification.broadcast.specialist;

import com.red_escuela.backend.notification.broadcast.domain.BroadcastDeliveryKind;
import com.red_escuela.backend.notification.broadcast.domain.OutboundMessage;
import com.red_escuela.backend.notification.broadcast.domain.PhoneNumberNormalizer;
import com.red_escuela.backend.notification.delivery.domain.DeliveryCategory;
import com.red_escuela.backend.notification.delivery.domain.DeliveryChannel;
import com.red_escuela.backend.notification.delivery.domain.DeliveryContent;
import com.red_escuela.backend.notification.delivery.domain.DeliveryEnvelope;
import com.red_escuela.backend.notification.delivery.domain.DeliveryStatusReason;
import com.red_escuela.backend.notification.delivery.domain.ProviderReceipt;
import com.red_escuela.backend.notification.delivery.domain.WhatsAppTemplateUse;
import com.red_escuela.backend.notification.delivery.entity.NotificationDelivery;
import com.red_escuela.backend.notification.delivery.repository.NotificationDeliveryRepository;
import com.red_escuela.backend.notification.delivery.specialist.EmailDeliveryChannel;
import com.red_escuela.backend.notification.delivery.specialist.WhatsAppProvider;
import com.red_escuela.backend.notification.delivery.specialist.WhatsAppProviderRejectedException;
import com.red_escuela.backend.identity.api.PersonContactFinder;
import com.red_escuela.backend.shared.exception.CircuitOpenException;
import com.red_escuela.backend.shared.resilience.CircuitBreaker;
import com.red_escuela.backend.shared.resilience.CircuitBreakerFactory;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
public class WhatsAppDeliverySender {

    public static final String CIRCUIT_NAME = "whatsapp-meta";
    static final String RELATED_ENTITY_TYPE = "BROADCAST";
    static final int DISPATCH_PRIORITY = 5;

    private static final Logger log = LoggerFactory.getLogger(WhatsAppDeliverySender.class);
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final NotificationDeliveryRepository deliveryRepository;
    private final PersonContactFinder personContactFinder;
    private final ObjectProvider<WhatsAppProvider> providerSource;
    private final CircuitBreaker breaker;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public WhatsAppDeliverySender(NotificationDeliveryRepository deliveryRepository, PersonContactFinder personContactFinder,
                                  ObjectProvider<WhatsAppProvider> providerSource, CircuitBreakerFactory circuitBreakerFactory,
                                  ObjectMapper objectMapper, Clock clock) {
        this.deliveryRepository = deliveryRepository;
        this.personContactFinder = personContactFinder;
        this.providerSource = providerSource;
        this.breaker = circuitBreakerFactory.breakerNamed(CIRCUIT_NAME);
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public Long enqueue(OutboundMessage message, WhatsAppTemplateUse template) {
        String dedupKey = "BROADCAST_" + message.broadcastId() + "_" + message.recipientPersonId();
        Optional<NotificationDelivery> existing = deliveryRepository.findByDedupKeyAndChannel(dedupKey, DeliveryChannel.WHATSAPP_API);
        if (existing.isPresent()) {
            return existing.get().getId();
        }
        DeliveryEnvelope envelope = new DeliveryEnvelope(dedupKey, DeliveryCategory.BROADCAST, BroadcastDeliveryKind.of(message.category()),
                DISPATCH_PRIORITY, message.recipientPersonId(), RELATED_ENTITY_TYPE, message.broadcastId(), LocalDate.now(clock));
        NotificationDelivery delivery = NotificationDelivery.queuedWhatsApp(envelope,
                new DeliveryContent(null, message.content().body(), null), template, ApplicationTime.instant());
        return deliveryRepository.saveAndFlush(delivery).getId();
    }

    @Transactional
    public void sendQueued(Long deliveryId) {
        Optional<NotificationDelivery> locked = deliveryRepository.findByIdForUpdate(deliveryId);
        if (locked.isEmpty() || !locked.get().getStatus().isWaitingForDispatch()) {
            return;
        }
        WhatsAppProvider provider = providerSource.getIfAvailable();
        if (provider == null) {
            return;
        }
        NotificationDelivery delivery = locked.get();
        Optional<String> phone = personContactFinder.findPhoneNumber(delivery.getRecipientPersonId())
                .flatMap(PhoneNumberNormalizer::normalizeArgentineMobile);
        if (phone.isEmpty()) {
            delivery.suppress(DeliveryStatusReason.NO_WHATSAPP_NUMBER);
            return;
        }
        ProviderReceipt receipt = null;
        try {
            receipt = breaker.execute(() -> provider.sendTemplate(phone.get(), delivery.getTemplateName(), delivery.getTemplateLanguage(),
                    variablesOf(delivery)), failure -> !(failure instanceof WhatsAppProviderRejectedException));
        } catch (CircuitOpenException open) {
            log.warn("Circuito de WhatsApp abierto, la entrega queda sin consumir intentos dedup={}", delivery.getDedupKey());
            return;
        } catch (WhatsAppProviderRejectedException rejection) {
            log.warn("El proveedor rechazo el mensaje de WhatsApp sin reintento dedup={} status={} codigo={}", delivery.getDedupKey(),
                    rejection.statusCode(), rejection.errorCode());
            delivery.failForGood(DeliveryStatusReason.PROVIDER_REJECTED, rejection.errorCode());
            return;
        } catch (RuntimeException failure) {
            log.warn("El proveedor de WhatsApp fallo dedup={} causa={}", delivery.getDedupKey(), failure.getClass().getSimpleName());
        }
        if (receipt == null) {
            delivery.recordFailedAttempt(DeliveryStatusReason.PROVIDER_ERROR, EmailDeliveryChannel.MAX_SEND_ATTEMPTS);
        } else {
            delivery.markSent(receipt.providerMessageId(), false, ApplicationTime.instant());
        }
    }

    private List<String> variablesOf(NotificationDelivery delivery) {
        return objectMapper.readValue(delivery.getTemplateVariables(), STRING_LIST);
    }
}
