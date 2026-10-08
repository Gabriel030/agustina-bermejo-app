package com.red_escuela.backend.notification;

import com.red_escuela.backend.enrollment.events.ProgramApplicationApprovedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ProgramApplicationApprovedNotificationListener {

    private final ApplicationEventPublisher eventPublisher;

    public ProgramApplicationApprovedNotificationListener(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @EventListener
    public void notifyStudentOfApprovedApplication(ProgramApplicationApprovedEvent event) {
        eventPublisher.publishEvent(new ProgramApplicationApprovedNotification(event.applicationId(), event.student(), event.programName()));
    }
}
