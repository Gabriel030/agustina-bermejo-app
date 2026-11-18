package com.red_escuela.backend.identity.controller;

import com.red_escuela.backend.identity.dto.CancelEmailChangeRequest;
import com.red_escuela.backend.identity.usecase.emailverification.CancelEmailChangeUseCase;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CancelEmailChangeController {

    private final CancelEmailChangeUseCase cancelEmailChangeUseCase;

    public CancelEmailChangeController(CancelEmailChangeUseCase cancelEmailChangeUseCase) {
        this.cancelEmailChangeUseCase = cancelEmailChangeUseCase;
    }

    @PostMapping("/cancel-email-change")
    public void cancelEmailChange(@Valid @RequestBody CancelEmailChangeRequest request) {
        cancelEmailChangeUseCase.cancelFromPreviousEmail(request.token());
    }
}
