package com.red_escuela.backend.notification.broadcast.specialist;

import com.red_escuela.backend.identity.api.PersonContactFinder;
import com.red_escuela.backend.notification.broadcast.domain.ChannelAddress;
import com.red_escuela.backend.notification.broadcast.domain.ChannelCapabilities;
import com.red_escuela.backend.notification.broadcast.domain.ChannelType;
import com.red_escuela.backend.notification.broadcast.domain.DeliveryMode;
import com.red_escuela.backend.notification.broadcast.domain.DenialReason;
import com.red_escuela.backend.notification.broadcast.domain.DispatchOutcome;
import com.red_escuela.backend.notification.broadcast.domain.OutboundMessage;
import com.red_escuela.backend.notification.broadcast.domain.PhoneNumberNormalizer;
import com.red_escuela.backend.notification.broadcast.policy.BroadcastDeliveryContext;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class WhatsAppCloudMessageChannel implements MessageChannel {

    private static final ChannelCapabilities CAPABILITIES = new ChannelCapabilities(false, true, true, 1024);

    private final PersonContactFinder personContactFinder;
    private final ContactConsentLedger contactConsentLedger;
    private final PolicySet<BroadcastDeliveryContext> broadcastDeliveryPolicySet;
    private final BroadcastChannelGate channelGate;
    private final WhatsAppTemplateResolver templateResolver;
    private final WhatsAppDeliverySender deliverySender;

    public WhatsAppCloudMessageChannel(PersonContactFinder personContactFinder, ContactConsentLedger contactConsentLedger,
                                       PolicySet<BroadcastDeliveryContext> broadcastDeliveryPolicySet, BroadcastChannelGate channelGate,
                                       WhatsAppTemplateResolver templateResolver, WhatsAppDeliverySender deliverySender) {
        this.personContactFinder = personContactFinder;
        this.contactConsentLedger = contactConsentLedger;
        this.broadcastDeliveryPolicySet = broadcastDeliveryPolicySet;
        this.channelGate = channelGate;
        this.templateResolver = templateResolver;
        this.deliverySender = deliverySender;
    }

    @Override
    public ChannelType type() {
        return ChannelType.WHATSAPP_API;
    }

    @Override
    public DeliveryMode deliveryMode() {
        return DeliveryMode.AUTOMATIC;
    }

    @Override
    public ChannelCapabilities capabilities() {
        return CAPABILITIES;
    }

    @Override
    public Optional<ChannelAddress> resolveAddress(Long personId) {
        return personContactFinder.findPhoneNumber(personId)
                .flatMap(PhoneNumberNormalizer::normalizeArgentineMobile)
                .map(ChannelAddress::ofPhone);
    }

    @Override
    public DispatchOutcome dispatch(OutboundMessage message) {
        channelGate.requireEnabled(type());
        BroadcastDeliveryContext context = new BroadcastDeliveryContext(message.recipientPersonId(), type(), message.category(),
                contactConsentLedger.isAllowed(message.recipientPersonId(), type(), message.category()));
        return broadcastDeliveryPolicySet.evaluateRulesUntilFirstViolation(context).firstDenial()
                .<DispatchOutcome>map(denial -> new DispatchOutcome.Rejected(DenialReason.valueOf(denial.code())))
                .orElseGet(() -> enqueue(message));
    }

    private DispatchOutcome enqueue(OutboundMessage message) {
        return new DispatchOutcome.Queued(deliverySender.enqueue(message,
                templateResolver.resolve(message.broadcastId(), message.recipientPersonId(), message.category())));
    }
}
