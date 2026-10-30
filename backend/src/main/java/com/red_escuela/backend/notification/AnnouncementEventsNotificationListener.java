package com.red_escuela.backend.notification;

import com.red_escuela.backend.announcement.events.AnnouncementDeletedEvent;
import com.red_escuela.backend.announcement.events.AnnouncementPublishedEvent;
import com.red_escuela.backend.announcement.events.AnnouncementUpdatedEvent;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.repository.NotificationRepository;
import com.red_escuela.backend.shared.event.ProcessedEffectGuard;
import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AnnouncementEventsNotificationListener {
    static final String NOTIFY_ANNOUNCEMENT_PUBLISHED_HANDLER = "AnnouncementEventsNotificationListener.notifyAnnouncementPublished";
    static final String NOTIFY_ANNOUNCEMENT_UPDATED_HANDLER = "AnnouncementEventsNotificationListener.notifyAnnouncementUpdated";
    static final String REMOVE_UNREAD_ANNOUNCEMENT_NOTIFICATIONS_HANDLER = "AnnouncementEventsNotificationListener.removeUnreadAnnouncementNotifications";
    private static final String ANNOUNCEMENT_ENTITY_TYPE = "Announcement";

    private final ProcessedEffectGuard effectGuard;
    private final ApplicationEventPublisher eventPublisher;
    private final EntityManager entityManager;
    private final NotificationRepository notificationRepository;

    public AnnouncementEventsNotificationListener(ProcessedEffectGuard effectGuard, ApplicationEventPublisher eventPublisher, EntityManager entityManager, NotificationRepository notificationRepository) {
        this.effectGuard = effectGuard;
        this.eventPublisher = eventPublisher;
        this.entityManager = entityManager;
        this.notificationRepository = notificationRepository;
    }

    @ApplicationModuleListener
    public void notifyAnnouncementPublished(AnnouncementPublishedEvent event) {
        effectGuard.runEffectOnce(DerivedEventIdentity.of(event), NOTIFY_ANNOUNCEMENT_PUBLISHED_HANDLER, () -> eventPublisher.publishEvent(new AnnouncementPublishedNotification(event.announcementId(), event.sectionName(), personsOf(event.studentPersonIds()))));
    }

    @ApplicationModuleListener
    public void notifyAnnouncementUpdated(AnnouncementUpdatedEvent event) {
        effectGuard.runEffectOnce(DerivedEventIdentity.of(event), NOTIFY_ANNOUNCEMENT_UPDATED_HANDLER, () -> eventPublisher.publishEvent(new AnnouncementUpdatedNotification(event.announcementId(), event.sectionName(), personsOf(event.studentPersonIds()))));
    }

    @ApplicationModuleListener
    public void removeUnreadAnnouncementNotifications(AnnouncementDeletedEvent event) {
        effectGuard.runEffectOnce(DerivedEventIdentity.of(event), REMOVE_UNREAD_ANNOUNCEMENT_NOTIFICATIONS_HANDLER, () -> notificationRepository.deleteUnreadByRelatedEntity(ANNOUNCEMENT_ENTITY_TYPE, event.announcementId()));
    }

    private List<Person> personsOf(List<Long> personIds) {
        return personIds.stream().map(personId -> entityManager.getReference(Person.class, personId)).toList();
    }
}
