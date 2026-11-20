package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.identity.specialist.CurrentPersonService;
import org.springframework.stereotype.Component;

@Component
public class StaffPersonResolver {

    private final CurrentPersonService currentPersonService;

    public StaffPersonResolver(CurrentPersonService currentPersonService) {
        this.currentPersonService = currentPersonService;
    }

    public Long currentStaffPersonId() {
        return currentPersonService.resolveCurrentPerson().getId();
    }
}
