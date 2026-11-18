package com.red_escuela.backend.platform.controller;

import com.red_escuela.backend.platform.domain.WhatsAppApiSettingChange;
import com.red_escuela.backend.platform.dto.ChangeWhatsAppApiSettingRequest;
import com.red_escuela.backend.platform.dto.WhatsAppApiSettingResponse;
import com.red_escuela.backend.platform.usecase.settings.ChangeWhatsAppApiSettingUseCase;
import com.red_escuela.backend.platform.usecase.settings.GetWhatsAppApiSettingUseCase;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/institute-settings/notifications/whatsapp")
@PreAuthorize("hasRole('DIRECTOR')")
public class WhatsAppApiSettingsController {

    private final GetWhatsAppApiSettingUseCase getSetting;
    private final ChangeWhatsAppApiSettingUseCase changeSetting;

    public WhatsAppApiSettingsController(GetWhatsAppApiSettingUseCase getSetting, ChangeWhatsAppApiSettingUseCase changeSetting) {
        this.getSetting = getSetting;
        this.changeSetting = changeSetting;
    }

    @GetMapping
    public WhatsAppApiSettingResponse getSetting() {
        return WhatsAppApiSettingResponse.of(getSetting.read());
    }

    @PutMapping
    public WhatsAppApiSettingResponse changeSetting(@Valid @RequestBody ChangeWhatsAppApiSettingRequest request) {
        return WhatsAppApiSettingResponse.of(changeSetting.change(new WhatsAppApiSettingChange(request.enabled(), request.reason())));
    }
}
