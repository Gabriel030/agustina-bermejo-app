package com.red_escuela.backend.notification.controller;

import com.red_escuela.backend.notification.dto.NotificationResponse;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import com.red_escuela.backend.notification.service.NotificationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class MeNotificationController {

    private final NotificationService notificationService;
    private final CurrentPersonService currentPersonService;

    public MeNotificationController(NotificationService notificationService, CurrentPersonService currentPersonService) {
        this.notificationService = notificationService;
        this.currentPersonService = currentPersonService;
    }

    @GetMapping("/me/notifications")
    public List<NotificationResponse> mine() {
        Long personId = currentPersonService.resolveCurrentPerson().getId();
        return notificationService.listForPerson(personId).stream().map(NotificationResponse::from).toList();
    }

    @PostMapping("/me/notifications/{id}/read")
    public NotificationResponse markAsRead(@PathVariable Long id) {
        Long personId = currentPersonService.resolveCurrentPerson().getId();
        return NotificationResponse.from(notificationService.markAsRead(id, personId));
    }
}
