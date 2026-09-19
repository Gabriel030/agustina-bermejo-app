package com.red_escuela.backend.identity.controller;

import com.red_escuela.backend.identity.usecase.emailverification.VerifyEmailUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VerifyEmailController {

    private final VerifyEmailUseCase verifyEmailUseCase;

    public VerifyEmailController(VerifyEmailUseCase verifyEmailUseCase) {
        this.verifyEmailUseCase = verifyEmailUseCase;
    }

    @GetMapping("/verify-email")
    public void verifyEmail(@RequestParam String token) {
        verifyEmailUseCase.verify(token);
    }
}
