package com.red_escuela.backend.enrollment.controller;

import com.red_escuela.backend.enrollment.dto.OwnWaitlistPositionResponse;
import com.red_escuela.backend.enrollment.usecase.waitlist.ListOwnWaitlistPositionsUseCase;
import com.red_escuela.backend.identity.specialist.CurrentStudentService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class MeWaitlistController {

    private final CurrentStudentService currentStudentService;
    private final ListOwnWaitlistPositionsUseCase listOwnWaitlistPositionsUseCase;

    public MeWaitlistController(CurrentStudentService currentStudentService,
                               ListOwnWaitlistPositionsUseCase listOwnWaitlistPositionsUseCase) {
        this.currentStudentService = currentStudentService;
        this.listOwnWaitlistPositionsUseCase = listOwnWaitlistPositionsUseCase;
    }

    @GetMapping("/me/waitlist-positions")
    @PreAuthorize("hasRole('ALUMNO')")
    public List<OwnWaitlistPositionResponse> listOwnPositions() {
        return listOwnWaitlistPositionsUseCase.list(currentStudentService.resolveCurrentStudent().getId());
    }
}
