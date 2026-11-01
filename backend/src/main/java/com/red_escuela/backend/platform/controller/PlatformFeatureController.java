package com.red_escuela.backend.platform.controller;

import com.red_escuela.backend.platform.domain.BillingModeChangeClearance;
import com.red_escuela.backend.platform.domain.FeatureChange;
import com.red_escuela.backend.platform.domain.FeatureTransitionRequest;
import com.red_escuela.backend.platform.dto.ChangeFeatureRequest;
import com.red_escuela.backend.platform.dto.FeatureStateResponse;
import com.red_escuela.backend.platform.dto.FeatureTransitionPreviewResponse;
import com.red_escuela.backend.platform.model.BillingMode;
import com.red_escuela.backend.platform.model.FeatureCode;
import com.red_escuela.backend.platform.usecase.ChangePlatformFeatureUseCase;
import com.red_escuela.backend.platform.usecase.PreviewPlatformFeatureChangeUseCase;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PlatformFeatureController {

    private final ChangePlatformFeatureUseCase changeFeature;
    private final PreviewPlatformFeatureChangeUseCase previewFeatureChange;

    public PlatformFeatureController(ChangePlatformFeatureUseCase changeFeature, PreviewPlatformFeatureChangeUseCase previewFeatureChange) {
        this.changeFeature = changeFeature;
        this.previewFeatureChange = previewFeatureChange;
    }

    @GetMapping("/platform/features/{code}/preview")
    public FeatureTransitionPreviewResponse previewFeatureChange(@PathVariable FeatureCode code, @RequestParam BillingMode target) {
        return FeatureTransitionPreviewResponse.of(previewFeatureChange.preview(new FeatureTransitionRequest(code, target)));
    }

    @PutMapping("/platform/features/{code}")
    public FeatureStateResponse changeFeature(
            @PathVariable FeatureCode code,
            @RequestHeader(value = "If-Match", required = false) Long expectedVersion,
            @Valid @RequestBody ChangeFeatureRequest request) {
        FeatureChange change = new FeatureChange(code, request.enabled(), request.mode(), expectedVersion, request.reason(), BillingModeChangeClearance.NONE, request.previewConfirmationToken());
        return FeatureStateResponse.of(code, changeFeature.change(change));
    }
}
