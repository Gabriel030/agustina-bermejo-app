package com.red_escuela.backend.notification.controller;

import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.notification.dto.NotificationResponse;
import com.red_escuela.backend.notification.dto.UnreadNotificationCountResponse;
import com.red_escuela.backend.notification.usecase.inbox.CountMyUnreadNotificationsUseCase;
import com.red_escuela.backend.notification.usecase.inbox.ListMyNotificationsUseCase;
import com.red_escuela.backend.notification.usecase.inbox.MarkMyNotificationAsReadUseCase;
import com.red_escuela.backend.shared.pagination.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeNotificationController {

    private final ListMyNotificationsUseCase listMyNotificationsUseCase;
    private final CountMyUnreadNotificationsUseCase countMyUnreadNotificationsUseCase;
    private final MarkMyNotificationAsReadUseCase markMyNotificationAsReadUseCase;
    private final CurrentActorFinder currentActorFinder;

    public MeNotificationController(ListMyNotificationsUseCase listMyNotificationsUseCase,
                                    CountMyUnreadNotificationsUseCase countMyUnreadNotificationsUseCase,
                                    MarkMyNotificationAsReadUseCase markMyNotificationAsReadUseCase,
                                    CurrentActorFinder currentActorFinder) {
        this.listMyNotificationsUseCase = listMyNotificationsUseCase;
        this.countMyUnreadNotificationsUseCase = countMyUnreadNotificationsUseCase;
        this.markMyNotificationAsReadUseCase = markMyNotificationAsReadUseCase;
        this.currentActorFinder = currentActorFinder;
    }

    @GetMapping("/me/notifications")
    public PageResponse<NotificationResponse> mine(@RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return listMyNotificationsUseCase.list(currentActorFinder.currentPersonId(), page, size);
    }

    @GetMapping("/me/notifications/unread-count")
    public UnreadNotificationCountResponse unreadCount() {
        return countMyUnreadNotificationsUseCase.count(currentActorFinder.currentPersonId());
    }

    @PostMapping("/me/notifications/{id}/read")
    public NotificationResponse markAsRead(@PathVariable Long id) {
        return markMyNotificationAsReadUseCase.markAsRead(id, currentActorFinder.currentPersonId());
    }
}
