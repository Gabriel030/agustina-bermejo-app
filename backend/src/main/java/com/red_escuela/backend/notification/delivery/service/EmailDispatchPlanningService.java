package com.red_escuela.backend.notification.delivery.service;

import com.red_escuela.backend.notification.delivery.domain.DeliveryCategory;
import com.red_escuela.backend.notification.delivery.domain.DeliveryChannel;
import com.red_escuela.backend.notification.delivery.domain.DeliveryStatus;
import com.red_escuela.backend.notification.delivery.domain.DeliveryStatusReason;
import com.red_escuela.backend.notification.delivery.domain.DispatchCandidate;
import com.red_escuela.backend.notification.delivery.domain.DispatchItem;
import com.red_escuela.backend.notification.delivery.domain.EmailDispatchPlan;
import com.red_escuela.backend.notification.delivery.domain.EmailDispatchPlanner;
import com.red_escuela.backend.notification.delivery.domain.EmailDispatchSettings;
import com.red_escuela.backend.notification.delivery.domain.PlannedDispatch;
import com.red_escuela.backend.notification.delivery.entity.NotificationDelivery;
import com.red_escuela.backend.notification.delivery.repository.NotificationDeliveryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class EmailDispatchPlanningService {

    private static final Logger log = LoggerFactory.getLogger(EmailDispatchPlanningService.class);
    private static final List<DeliveryStatus> WAITING = List.of(DeliveryStatus.QUEUED, DeliveryStatus.DEFERRED);
    private static final List<DeliveryStatus> SENT = List.of(DeliveryStatus.SENT, DeliveryStatus.SENT_SIMULATED);
    private static final LocalDate NEVER_DUE = LocalDate.of(9999, 12, 31);

    private final NotificationDeliveryRepository deliveryRepository;
    private final EmailDispatchSource dispatchSource;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;
    private final EmailDispatchPlanner planner = new EmailDispatchPlanner();

    public EmailDispatchPlanningService(NotificationDeliveryRepository deliveryRepository, EmailDispatchSource dispatchSource, Clock clock,
                                         TransactionTemplate transactionTemplate) {
        this.deliveryRepository = deliveryRepository;
        this.dispatchSource = dispatchSource;
        this.clock = clock;
        this.transactionTemplate = transactionTemplate;
    }

    public List<Long> planAndApplyFor(LocalDate today, EmailDispatchSettings settings, LocalDate nextBusinessDay) {
        return transactionTemplate.execute(status -> planAndApplyInsideTransaction(today, settings, nextBusinessDay));
    }

    private List<Long> planAndApplyInsideTransaction(LocalDate today, EmailDispatchSettings settings, LocalDate nextBusinessDay) {
        List<NotificationDelivery> locked = deliveryRepository.lockDispatchableByPriority(DeliveryChannel.EMAIL, WAITING, today, clock.instant());
        if (locked.isEmpty()) {
            return List.of();
        }
        Map<Long, NotificationDelivery> stillApplicable = new HashMap<>();
        int suppressed = 0;
        for (NotificationDelivery delivery : locked) {
            if (dispatchSource.isStillApplicable(delivery, today)) {
                stillApplicable.put(delivery.getId(), delivery);
            } else {
                delivery.suppress(DeliveryStatusReason.NO_LONGER_APPLICABLE);
                suppressed++;
            }
        }
        List<DispatchCandidate> candidates = new ArrayList<>();
        for (NotificationDelivery delivery : locked) {
            if (stillApplicable.containsKey(delivery.getId())) {
                candidates.add(new DispatchCandidate(delivery.getId(), itemsOf(delivery)));
            }
        }
        int remaining = remainingCapacityOn(today, settings.dailyCap());
        EmailDispatchPlan plan = planner.planFor(candidates, today, nextBusinessDay, remaining);
        plan.toDiscard().forEach(discard -> stillApplicable.get(discard.deliveryId()).discard(discard.reason()));
        for (PlannedDispatch deferral : plan.toDefer()) {
            stillApplicable.get(deferral.deliveryId()).deferTo(DeliveryStatusReason.DAILY_CAP_REACHED, deferral.scheduledFor(),
                    deferral.scheduledFor().atStartOfDay(clock.getZone()).toInstant());
        }
        int trimmed = 0;
        List<Long> toSend = new ArrayList<>();
        for (PlannedDispatch dispatch : plan.toSend()) {
            if (dispatch.dropsItems()) {
                NotificationDelivery delivery = stillApplicable.get(dispatch.deliveryId());
                Set<String> droppedKeys = new HashSet<>();
                dispatch.droppedItems().forEach(item -> droppedKeys.add(item.key()));
                dispatchSource.contentWithoutItems(delivery, droppedKeys).ifPresent(delivery::replaceEmailContent);
                trimmed++;
            }
            toSend.add(dispatch.deliveryId());
        }
        log.info("Planificacion de despacho fecha={} candidatos={} cupoRestante={} aEnviar={} diferidos={} descartados={} suprimidos={} recortados={}",
                today, locked.size(), remaining, plan.toSend().size(), plan.toDefer().size(), plan.toDiscard().size(), suppressed, trimmed);
        return List.copyOf(toSend);
    }

    private List<DispatchItem> itemsOf(NotificationDelivery delivery) {
        List<DispatchItem> items = dispatchSource.itemsOf(delivery);
        if (!items.isEmpty()) {
            return items;
        }
        return List.of(new DispatchItem("delivery:" + delivery.getId(), NEVER_DUE, false, delivery.getPriority()));
    }

    private int remainingCapacityOn(LocalDate today, int dailyCap) {
        Instant from = today.atStartOfDay(clock.getZone()).toInstant();
        Instant until = today.plusDays(1).atStartOfDay(clock.getZone()).toInstant();
        long sentToday = deliveryRepository.countSentBetween(DeliveryChannel.EMAIL, DeliveryCategory.COLLECTION_REMINDER, SENT, from, until);
        return (int) Math.max(0L, dailyCap - sentToday);
    }
}
