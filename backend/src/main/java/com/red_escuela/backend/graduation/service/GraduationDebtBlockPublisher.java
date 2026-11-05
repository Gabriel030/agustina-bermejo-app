package com.red_escuela.backend.graduation.service;

import com.red_escuela.backend.graduation.events.GraduationRequestDebtBlockedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GraduationDebtBlockPublisher {

    private final ApplicationEventPublisher eventPublisher;

    public GraduationDebtBlockPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishDebtBlocked(GraduationRequestDebtBlockedEvent event) {
        eventPublisher.publishEvent(event);
    }
}
