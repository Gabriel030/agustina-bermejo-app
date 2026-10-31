package com.red_escuela.backend.platform.controller;

import com.red_escuela.backend.platform.domain.BillingModeChangeClearance;
import com.red_escuela.backend.platform.domain.FeatureChange;
import com.red_escuela.backend.platform.dto.ChangeFeatureRequest;
import com.red_escuela.backend.platform.dto.FeatureStateResponse;
import com.red_escuela.backend.platform.model.FeatureCode;
import com.red_escuela.backend.platform.usecase.ChangePlatformFeatureUseCase;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PlatformFeatureController {

    private final ChangePlatformFeatureUseCase changeFeature;

    public PlatformFeatureController(ChangePlatformFeatureUseCase changeFeature) {
        this.changeFeature = changeFeature;
    }

    @PutMapping("/platform/features/{code}")
    public FeatureStateResponse changeFeature(
            @PathVariable FeatureCode code,
            @RequestHeader(value = "If-Match", required = false) Long expectedVersion,
            @Valid @RequestBody ChangeFeatureRequest request) {
        FeatureChange change = new FeatureChange(code, request.enabled(), request.mode(), expectedVersion, request.reason(), BillingModeChangeClearance.NONE);
        return FeatureStateResponse.of(code, changeFeature.change(change));
    }
}
