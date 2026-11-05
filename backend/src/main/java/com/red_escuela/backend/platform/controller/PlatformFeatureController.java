package com.red_escuela.backend.platform.controller;

import com.red_escuela.backend.platform.domain.BillingModeChangeClearance;
import com.red_escuela.backend.platform.domain.FeatureChange;
import com.red_escuela.backend.platform.domain.FeatureChangeFilter;
import com.red_escuela.backend.platform.domain.FeatureTransitionRequest;
import com.red_escuela.backend.platform.dto.ChangeFeatureRequest;
import com.red_escuela.backend.platform.dto.FeatureStateResponse;
import com.red_escuela.backend.platform.dto.FeatureTransitionPreviewResponse;
import com.red_escuela.backend.shared.module.BillingMode;
import com.red_escuela.backend.platform.model.FeatureCode;
import com.red_escuela.backend.platform.usecase.ChangePlatformFeatureUseCase;
import com.red_escuela.backend.platform.usecase.PreviewPlatformFeatureChangeUseCase;
import com.red_escuela.backend.platform.dto.FeatureChangeResponse;
import com.red_escuela.backend.platform.dto.FeatureDetailResponse;
import com.red_escuela.backend.platform.usecase.ListFeatureChangesUseCase;
import com.red_escuela.backend.platform.usecase.ListPlatformFeaturesUseCase;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import java.util.List;
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
    private final ListPlatformFeaturesUseCase listFeatures;
    private final ListFeatureChangesUseCase listFeatureChanges;

    public PlatformFeatureController(ChangePlatformFeatureUseCase changeFeature, PreviewPlatformFeatureChangeUseCase previewFeatureChange,
                                     ListPlatformFeaturesUseCase listFeatures, ListFeatureChangesUseCase listFeatureChanges) {
        this.changeFeature = changeFeature;
        this.previewFeatureChange = previewFeatureChange;
        this.listFeatures = listFeatures;
        this.listFeatureChanges = listFeatureChanges;
    }

    @GetMapping("/platform/features")
    public List<FeatureDetailResponse> listFeatures() {
        return listFeatures.list().stream().map(FeatureDetailResponse::of).toList();
    }

    @GetMapping("/platform/features/changes")
    public Page<FeatureChangeResponse> listFeatureChanges(@RequestParam(required = false) FeatureCode code, @PageableDefault(size = 25) Pageable pageable) {
        return listFeatureChanges.list(new FeatureChangeFilter(code), pageable).map(FeatureChangeResponse::of);
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
