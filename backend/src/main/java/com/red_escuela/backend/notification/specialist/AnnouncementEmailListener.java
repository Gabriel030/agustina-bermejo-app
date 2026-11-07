package com.red_escuela.backend.notification.specialist;

import com.red_escuela.backend.announcement.events.AnnouncementPublishedEvent;
import com.red_escuela.backend.announcement.events.AnnouncementUpdatedEvent;
import com.red_escuela.backend.identity.api.InstitutionAcademicSettingsReader;
import com.red_escuela.backend.identity.api.NotificationPreferenceFinder;
import com.red_escuela.backend.identity.entity.NotificationChannel;
import com.red_escuela.backend.identity.entity.NotificationEvent;
import com.red_escuela.backend.notification.delivery.domain.DeliveryCategory;
import com.red_escuela.backend.notification.delivery.domain.DeliveryContent;
import com.red_escuela.backend.notification.delivery.domain.DeliveryEnvelope;
import com.red_escuela.backend.notification.delivery.specialist.DeliveryRequest;
import com.red_escuela.backend.notification.delivery.specialist.NotificationDeliveryEnqueuer;
import com.red_escuela.backend.shared.event.ProcessedEffectGuard;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Component
public class AnnouncementEmailListener {
    static final String PUBLISHED_HANDLER = "AnnouncementEmailListener.emailAnnouncementPublished";
    static final String UPDATED_HANDLER = "AnnouncementEmailListener.emailAnnouncementUpdated";
    static final String RELATED_ENTITY_TYPE = "Announcement";
    static final String PUBLISHED_KIND = "PUBLISHED";
    static final String UPDATED_KIND = "UPDATED";

    private final ProcessedEffectGuard effectGuard;
    private final NotificationDeliveryEnqueuer deliveryService;
    private final NotificationPreferenceFinder preferenceFinder;
    private final InstitutionAcademicSettingsReader settingsReader;
    private final Clock clock;

    public AnnouncementEmailListener(ProcessedEffectGuard effectGuard, NotificationDeliveryEnqueuer deliveryService,
                                     NotificationPreferenceFinder preferenceFinder, InstitutionAcademicSettingsReader settingsReader, Clock clock) {
        this.effectGuard = effectGuard;
        this.deliveryService = deliveryService;
        this.preferenceFinder = preferenceFinder;
        this.settingsReader = settingsReader;
        this.clock = clock;
    }

    @ApplicationModuleListener
    public void emailAnnouncementPublished(AnnouncementPublishedEvent event) {
        effectGuard.runEffectOnce(DerivedEventIdentity.of(event), PUBLISHED_HANDLER,
                () -> enqueueEmails(event.announcementId(), event.sectionName(), event.title(), event.urgent(), event.studentPersonIds(), PUBLISHED_KIND));
    }

    @ApplicationModuleListener
    public void emailAnnouncementUpdated(AnnouncementUpdatedEvent event) {
        effectGuard.runEffectOnce(DerivedEventIdentity.of(event), UPDATED_HANDLER,
                () -> enqueueEmails(event.announcementId(), event.sectionName(), event.title(), event.urgent(), event.studentPersonIds(), UPDATED_KIND));
    }

    private void enqueueEmails(Long announcementId, String sectionName, String title, boolean urgent, List<Long> personIds, String kind) {
        if (!urgent && !settingsReader.read().announcementEmailEnabled()) {
            return;
        }
        DeliveryContent content = AnnouncementEmailComposer.compose(title, sectionName, urgent, kind);
        LocalDate today = LocalDate.now(clock);
        for (Long personId : personIds) {
            if (!urgent && !preferenceFinder.isEnabled(personId, NotificationEvent.INSTITUTIONAL_NOTICE, NotificationChannel.EMAIL)) {
                continue;
            }
            DeliveryEnvelope envelope = new DeliveryEnvelope("announcement:" + announcementId + ":" + personId + ":" + kind,
                    DeliveryCategory.ANNOUNCEMENT, "ANNOUNCEMENT_" + kind, DeliveryEnvelope.HIGHEST_PRIORITY, personId,
                    RELATED_ENTITY_TYPE, announcementId, today);
            deliveryService.enqueue(new DeliveryRequest(envelope, null, null, content, false));
        }
    }
}
