package com.red_escuela.backend.platform.controller;

import com.red_escuela.backend.platform.domain.BillingNotificationsSettingChange;
import com.red_escuela.backend.platform.domain.PaymentMethodSettingChange;
import com.red_escuela.backend.platform.dto.BillingNotificationsSettingResponse;
import com.red_escuela.backend.platform.dto.ChangeInstituteSettingRequest;
import com.red_escuela.backend.platform.dto.InstituteSettingChangeResponse;
import com.red_escuela.backend.platform.dto.InstituteSettingsResponse;
import com.red_escuela.backend.platform.dto.PaymentMethodSettingResponse;
import com.red_escuela.backend.platform.model.PaymentMethodCode;
import com.red_escuela.backend.platform.usecase.settings.ChangeBillingNotificationsSettingUseCase;
import com.red_escuela.backend.platform.usecase.settings.ChangePaymentMethodSettingUseCase;
import com.red_escuela.backend.platform.usecase.settings.GetInstituteSettingsUseCase;
import com.red_escuela.backend.platform.usecase.settings.ListInstituteSettingChangesUseCase;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InstituteSettingsController {

    private final GetInstituteSettingsUseCase getSettings;
    private final ChangePaymentMethodSettingUseCase changePaymentMethod;
    private final ChangeBillingNotificationsSettingUseCase changeBillingNotifications;
    private final ListInstituteSettingChangesUseCase listChanges;

    public InstituteSettingsController(GetInstituteSettingsUseCase getSettings, ChangePaymentMethodSettingUseCase changePaymentMethod,
                                       ChangeBillingNotificationsSettingUseCase changeBillingNotifications, ListInstituteSettingChangesUseCase listChanges) {
        this.getSettings = getSettings;
        this.changePaymentMethod = changePaymentMethod;
        this.changeBillingNotifications = changeBillingNotifications;
        this.listChanges = listChanges;
    }

    @GetMapping("/institute-settings")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO','DIRECTOR')")
    public InstituteSettingsResponse getSettings() {
        return InstituteSettingsResponse.of(getSettings.read());
    }

    @PutMapping("/institute-settings/payment-methods/{code}")
    @PreAuthorize("hasRole('DIRECTOR')")
    public PaymentMethodSettingResponse changePaymentMethod(
            @PathVariable PaymentMethodCode code,
            @RequestHeader(value = "If-Match", required = false) Long expectedVersion,
            @Valid @RequestBody ChangeInstituteSettingRequest request) {
        return PaymentMethodSettingResponse.of(changePaymentMethod.change(new PaymentMethodSettingChange(code, request.enabled(), expectedVersion, request.reason())));
    }

    @PutMapping("/institute-settings/billing-notifications")
    @PreAuthorize("hasRole('DIRECTOR')")
    public BillingNotificationsSettingResponse changeBillingNotifications(
            @RequestHeader(value = "If-Match", required = false) Long expectedVersion,
            @Valid @RequestBody ChangeInstituteSettingRequest request) {
        return BillingNotificationsSettingResponse.of(changeBillingNotifications.change(new BillingNotificationsSettingChange(request.enabled(), expectedVersion, request.reason())));
    }

    @GetMapping("/institute-settings/audit")
    @PreAuthorize("hasRole('DIRECTOR')")
    public Page<InstituteSettingChangeResponse> listChanges(@PageableDefault(size = 25) Pageable pageable) {
        return listChanges.list(pageable).map(InstituteSettingChangeResponse::of);
    }
}
