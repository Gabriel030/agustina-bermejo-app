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
import com.red_escuela.backend.shared.permission.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
    @RequiresPermission(code = "platform.read_institute_settings", legacyRoles = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public InstituteSettingsResponse getSettings() {
        return InstituteSettingsResponse.of(getSettings.read());
    }

    @PutMapping("/institute-settings/payment-methods/{code}")
    @RequiresPermission(code = "platform.change_payment_methods", legacyRoles = "ROLE_DIRECTOR")
    public PaymentMethodSettingResponse changePaymentMethod(
            @PathVariable PaymentMethodCode code,
            @RequestHeader(value = "If-Match", required = false) Long expectedVersion,
            @Valid @RequestBody ChangeInstituteSettingRequest request) {
        return PaymentMethodSettingResponse.of(changePaymentMethod.change(new PaymentMethodSettingChange(code, request.enabled(), expectedVersion, request.reason())));
    }

    @PutMapping("/institute-settings/billing-notifications")
    @RequiresPermission(code = "platform.change_billing_notifications", legacyRoles = "ROLE_DIRECTOR")
    public BillingNotificationsSettingResponse changeBillingNotifications(
            @RequestHeader(value = "If-Match", required = false) Long expectedVersion,
            @Valid @RequestBody ChangeInstituteSettingRequest request) {
        return BillingNotificationsSettingResponse.of(changeBillingNotifications.change(new BillingNotificationsSettingChange(request.enabled(), expectedVersion, request.reason())));
    }

    @GetMapping("/institute-settings/audit")
    @RequiresPermission(code = "platform.read_institute_settings_audit", legacyRoles = "ROLE_DIRECTOR")
    public Page<InstituteSettingChangeResponse> listChanges(@PageableDefault(size = 25) Pageable pageable) {
        return listChanges.list(pageable).map(InstituteSettingChangeResponse::of);
    }
}
