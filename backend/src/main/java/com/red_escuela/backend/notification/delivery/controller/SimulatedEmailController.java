package com.red_escuela.backend.notification.delivery.controller;

import com.red_escuela.backend.notification.delivery.dto.SimulatedEmailPageResponse;
import com.red_escuela.backend.notification.delivery.usecase.ListSimulatedEmailsUseCase;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile({"local", "demo"})
@RequestMapping("/notification-deliveries/simulated")
public class SimulatedEmailController {
    private final ListSimulatedEmailsUseCase listSimulatedEmailsUseCase;

    public SimulatedEmailController(ListSimulatedEmailsUseCase listSimulatedEmailsUseCase) {
        this.listSimulatedEmailsUseCase = listSimulatedEmailsUseCase;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public SimulatedEmailPageResponse list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return listSimulatedEmailsUseCase.listSimulatedEmails(page, size);
    }
}
