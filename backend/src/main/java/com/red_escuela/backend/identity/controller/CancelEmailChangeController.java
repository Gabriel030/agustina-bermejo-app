package com.red_escuela.backend.identity.controller;

import com.red_escuela.backend.identity.usecase.emailverification.CancelEmailChangeUseCase;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CancelEmailChangeController {

    private final CancelEmailChangeUseCase cancelEmailChangeUseCase;

    public CancelEmailChangeController(CancelEmailChangeUseCase cancelEmailChangeUseCase) {
        this.cancelEmailChangeUseCase = cancelEmailChangeUseCase;
    }

    @PostMapping("/cancel-email-change")
    public void cancelEmailChange(@RequestParam String token) {
        cancelEmailChangeUseCase.cancelFromPreviousEmail(token);
    }
}
