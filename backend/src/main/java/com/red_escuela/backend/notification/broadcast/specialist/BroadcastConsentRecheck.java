package com.red_escuela.backend.notification.broadcast.specialist;

import com.red_escuela.backend.notification.broadcast.domain.BroadcastCategory;
import com.red_escuela.backend.notification.broadcast.domain.BroadcastDeliveryKind;
import com.red_escuela.backend.notification.broadcast.domain.ChannelType;
import com.red_escuela.backend.notification.broadcast.policy.BroadcastDeliveryContext;
import com.red_escuela.backend.notification.delivery.domain.DeliveryChannel;
import com.red_escuela.backend.notification.delivery.entity.NotificationDelivery;
import com.red_escuela.backend.notification.delivery.repository.NotificationDeliveryRepository;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
public class BroadcastConsentRecheck {

    private static final Logger log = LoggerFactory.getLogger(BroadcastConsentRecheck.class);

    private final NotificationDeliveryRepository deliveryRepository;
    private final ContactConsentLedger contactConsentLedger;
    private final PolicySet<BroadcastDeliveryContext> broadcastDeliveryPolicySet;

    public BroadcastConsentRecheck(NotificationDeliveryRepository deliveryRepository, ContactConsentLedger contactConsentLedger,
                                   PolicySet<BroadcastDeliveryContext> broadcastDeliveryPolicySet) {
        this.deliveryRepository = deliveryRepository;
        this.contactConsentLedger = contactConsentLedger;
        this.broadcastDeliveryPolicySet = broadcastDeliveryPolicySet;
    }

    @Transactional
    public boolean suppressIfConsentWithdrawn(Long deliveryId) {
        Optional<NotificationDelivery> locked = deliveryRepository.findByIdForUpdate(deliveryId);
        if (locked.isEmpty()) {
            return false;
        }
        NotificationDelivery delivery = locked.get();
        if (!delivery.getStatus().isWaitingForDispatch()) {
            return false;
        }
        ChannelType channel = delivery.getChannel() == DeliveryChannel.WHATSAPP_API ? ChannelType.WHATSAPP_API : ChannelType.EMAIL;
        Optional<BroadcastCategory> broadcastCategory = BroadcastDeliveryKind.categoryOf(delivery.getKind());
        if (broadcastCategory.isEmpty()) {
            return false;
        }
        BroadcastCategory category = broadcastCategory.get();
        Long personId = delivery.getRecipientPersonId();
        BroadcastDeliveryContext context = new BroadcastDeliveryContext(personId, channel, category,
                contactConsentLedger.isAllowed(personId, channel, category));
        Optional<String> denialCode = broadcastDeliveryPolicySet.evaluateRulesUntilFirstViolation(context).firstDenial()
                .map(denial -> denial.code());
        denialCode.ifPresent(code -> {
            delivery.suppress(code);
            log.info("Mail masivo suprimido antes de enviar dedup={} motivo={} destinatario={}", delivery.getDedupKey(), code, personId);
        });
        return denialCode.isPresent();
    }
}
