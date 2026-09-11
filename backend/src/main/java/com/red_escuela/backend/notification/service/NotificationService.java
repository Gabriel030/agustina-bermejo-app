package com.red_escuela.backend.notification.service;

import com.red_escuela.backend.notification.entity.Notification;
import com.red_escuela.backend.notification.exception.NotOwnNotificationException;
import com.red_escuela.backend.notification.exception.NotificationNotFoundException;
import com.red_escuela.backend.notification.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public List<Notification> listForPerson(Long personId) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(personId);
    }

    @Transactional
    public Notification markAsRead(Long notificationId, Long personId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));
        if (!notification.getRecipient().getId().equals(personId)) {
            throw new NotOwnNotificationException(notificationId);
        }
        notification.setRead(true);
        return notificationRepository.save(notification);
    }
}
