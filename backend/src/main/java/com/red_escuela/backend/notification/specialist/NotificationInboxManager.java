package com.red_escuela.backend.notification.specialist;

import com.red_escuela.backend.notification.dto.NotificationResponse;
import com.red_escuela.backend.notification.entity.Notification;
import com.red_escuela.backend.notification.exception.NotificationNotFoundException;
import com.red_escuela.backend.notification.repository.NotificationRepository;
import com.red_escuela.backend.shared.pagination.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationInboxManager {

    private final NotificationRepository notificationRepository;

    public NotificationInboxManager(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> pageForPerson(Long personId, int page, int size) {
        int boundedPage = Math.max(page, 0);
        int boundedSize = Math.min(Math.max(size, 1), PageResponse.MAX_SIZE);
        Page<Notification> notifications = notificationRepository.findByRecipientId(personId,
                PageRequest.of(boundedPage, boundedSize, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        return new PageResponse<>(notifications.getContent().stream().map(NotificationResponse::from).toList(), boundedPage, boundedSize, notifications.getTotalElements());
    }

    @Transactional(readOnly = true)
    public long countUnreadForPerson(Long personId) {
        return notificationRepository.countByRecipientIdAndReadFalse(personId);
    }

    @Transactional
    public NotificationResponse markAsRead(Long notificationId, Long personId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));
        if (!notification.getRecipient().getId().equals(personId)) {
            throw new NotificationNotFoundException(notificationId);
        }
        notification.setRead(true);
        return NotificationResponse.from(notificationRepository.save(notification));
    }
}
