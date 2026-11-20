package com.red_escuela.backend.notification.broadcast.specialist;

import com.red_escuela.backend.identity.api.EmailRecipient;
import com.red_escuela.backend.identity.api.PersonContactFinder;
import com.red_escuela.backend.notification.broadcast.domain.BroadcastCategory;
import com.red_escuela.backend.notification.broadcast.domain.BroadcastDeliveryKind;
import com.red_escuela.backend.notification.broadcast.domain.ChannelAddress;
import com.red_escuela.backend.notification.broadcast.domain.ChannelCapabilities;
import com.red_escuela.backend.notification.broadcast.domain.ChannelType;
import com.red_escuela.backend.notification.broadcast.domain.ConsentDecision;
import com.red_escuela.backend.notification.broadcast.domain.DeliveryMode;
import com.red_escuela.backend.notification.broadcast.domain.DenialReason;
import com.red_escuela.backend.notification.broadcast.domain.DispatchOutcome;
import com.red_escuela.backend.notification.broadcast.domain.OutboundMessage;
import com.red_escuela.backend.notification.delivery.domain.DeliveryCategory;
import com.red_escuela.backend.notification.delivery.domain.DeliveryContent;
import com.red_escuela.backend.notification.delivery.domain.DeliveryEnvelope;
import com.red_escuela.backend.notification.broadcast.policy.BroadcastDeliveryContext;
import com.red_escuela.backend.notification.delivery.specialist.DeliveryRequest;
import com.red_escuela.backend.notification.delivery.specialist.NotificationDeliveryEnqueuer;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class EmailMessageChannel implements MessageChannel {

    static final String RELATED_ENTITY_TYPE = "BROADCAST";
    static final int DISPATCH_PRIORITY = 5;
    private static final ChannelCapabilities CAPABILITIES = new ChannelCapabilities(true, false, false, 50_000);

    private final PersonContactFinder personContactFinder;
    private final ContactConsentLedger contactConsentLedger;
    private final PolicySet<BroadcastDeliveryContext> broadcastDeliveryPolicySet;
    private final NotificationDeliveryEnqueuer deliveryEnqueuer;
    private final Clock clock;

    public EmailMessageChannel(PersonContactFinder personContactFinder, ContactConsentLedger contactConsentLedger,
                               PolicySet<BroadcastDeliveryContext> broadcastDeliveryPolicySet,
                               NotificationDeliveryEnqueuer deliveryEnqueuer, Clock clock) {
        this.personContactFinder = personContactFinder;
        this.contactConsentLedger = contactConsentLedger;
        this.broadcastDeliveryPolicySet = broadcastDeliveryPolicySet;
        this.deliveryEnqueuer = deliveryEnqueuer;
        this.clock = clock;
    }

    @Override
    public ChannelType type() {
        return ChannelType.EMAIL;
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
        return personContactFinder.findEmailRecipient(personId)
                .map(EmailRecipient::address)
                .map(address -> ChannelAddress.ofEmail(address.value()));
    }

    @Override
    public DispatchOutcome dispatch(OutboundMessage message) {
        return dispatchWithConsent(message, contactConsentLedger.isAllowed(message.recipientPersonId(), type(), message.category()));
    }

    @Override
    public List<DispatchOutcome> dispatchAll(List<OutboundMessage> messages) {
        Map<BroadcastCategory, Map<Long, ConsentDecision>> decisionsByCategory = new HashMap<>();
        messages.stream().map(OutboundMessage::category).distinct().forEach(category -> decisionsByCategory.put(category,
                contactConsentLedger.isAllowedAll(messages.stream().filter(message -> message.category() == category)
                        .map(OutboundMessage::recipientPersonId).toList(), type(), category)));
        return messages.stream()
                .map(message -> dispatchWithConsent(message, decisionsByCategory.get(message.category()).get(message.recipientPersonId())))
                .toList();
    }

    private DispatchOutcome dispatchWithConsent(OutboundMessage message, ConsentDecision consent) {
        BroadcastDeliveryContext context = new BroadcastDeliveryContext(message.recipientPersonId(), type(), message.category(), consent);
        return broadcastDeliveryPolicySet.evaluateRulesUntilFirstViolation(context).firstDenial()
                .<DispatchOutcome>map(denial -> new DispatchOutcome.Rejected(DenialReason.valueOf(denial.code())))
                .orElseGet(() -> enqueue(message));
    }

    private DispatchOutcome enqueue(OutboundMessage message) {
        DeliveryEnvelope envelope = new DeliveryEnvelope("BROADCAST_" + message.broadcastId() + "_" + message.recipientPersonId(),
                DeliveryCategory.BROADCAST, BroadcastDeliveryKind.of(message.category()), DISPATCH_PRIORITY, message.recipientPersonId(), RELATED_ENTITY_TYPE,
                message.broadcastId(), LocalDate.now(clock));
        DeliveryRequest request = new DeliveryRequest(envelope, null, null,
                DeliveryContent.forEmail(message.content().subject(), message.content().body(), null), false);
        Long deliveryId = deliveryEnqueuer.enqueueEmailAndFindDeliveryId(request)
                .orElseThrow(() -> new IllegalStateException("The broadcast delivery was not recorded"));
        return new DispatchOutcome.Queued(deliveryId);
    }
}
