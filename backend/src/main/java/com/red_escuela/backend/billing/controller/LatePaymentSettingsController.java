package com.red_escuela.backend.billing.controller;

import com.red_escuela.backend.billing.dto.LatePaymentSettingsResponse;
import com.red_escuela.backend.billing.dto.CreateLatePaymentSettingsRequest;
import com.red_escuela.backend.billing.usecase.latepaymentsettings.CreateLatePaymentSettingsUseCase;
import com.red_escuela.backend.billing.usecase.latepaymentsettings.FindCurrentLatePaymentSettingsUseCase;
import com.red_escuela.backend.shared.module.RequiresModule;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiresModule(RequiresModule.BILLING)
public class LatePaymentSettingsController {
    private final CreateLatePaymentSettingsUseCase createLatePaymentSettingsUseCase;
    private final FindCurrentLatePaymentSettingsUseCase findCurrentLatePaymentSettingsUseCase;

    public LatePaymentSettingsController(CreateLatePaymentSettingsUseCase createLatePaymentSettingsUseCase,
                                        FindCurrentLatePaymentSettingsUseCase findCurrentLatePaymentSettingsUseCase) {
        this.createLatePaymentSettingsUseCase = createLatePaymentSettingsUseCase;
        this.findCurrentLatePaymentSettingsUseCase = findCurrentLatePaymentSettingsUseCase;
    }

    @PostMapping("/late-payment-settings")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DIRECTOR')")
    public LatePaymentSettingsResponse create(@Valid @RequestBody CreateLatePaymentSettingsRequest request) {
        return LatePaymentSettingsResponse.from(createLatePaymentSettingsUseCase.create(request));
    }

    @GetMapping("/late-payment-settings/current")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public LatePaymentSettingsResponse current() {
        return LatePaymentSettingsResponse.from(findCurrentLatePaymentSettingsUseCase.findCurrent());
    }
}
