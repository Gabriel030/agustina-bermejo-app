package com.red_escuela.backend.billing.collections.operation;

import com.red_escuela.backend.billing.collections.service.OverdueContactSegment;
import com.red_escuela.backend.billing.collections.service.OverdueContactSegment.ContactListCounts;
import com.red_escuela.backend.billing.events.OverdueContactListUpdatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Component
public class OverdueContactListPublicationStep {
    private static final Logger log = LoggerFactory.getLogger(OverdueContactListPublicationStep.class);

    private final OverdueContactSegment segment;
    private final ApplicationEventPublisher eventPublisher;

    public OverdueContactListPublicationStep(OverdueContactSegment segment, ApplicationEventPublisher eventPublisher) {
        this.segment = segment;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ContactListCounts publishContactListOf(LocalDate businessDate) {
        ContactListCounts counts = segment.countContacts(businessDate);
        if (counts.total() > 0) {
            eventPublisher.publishEvent(OverdueContactListUpdatedEvent.updatedOn(businessDate, counts.total(), counts.newlyIn()));
        }
        log.info("Lista para contactar actualizada: fecha={}, alumnos={}, nuevos={}", businessDate, counts.total(), counts.newlyIn());
        return counts;
    }
}
