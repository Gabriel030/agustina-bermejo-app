package com.red_escuela.backend.platform.usecase;

import com.red_escuela.backend.platform.domain.FeatureTransitionPreview;
import com.red_escuela.backend.platform.domain.FeatureTransitionRequest;
import com.red_escuela.backend.platform.service.BillingTransitionPreviewer;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class PreviewPlatformFeatureChangeService implements PreviewPlatformFeatureChangeUseCase {

    private final BillingTransitionPreviewer transitionPreviewer;

    public PreviewPlatformFeatureChangeService(BillingTransitionPreviewer transitionPreviewer) {
        this.transitionPreviewer = transitionPreviewer;
    }

    @Override
    public FeatureTransitionPreview preview(FeatureTransitionRequest request) {
        return transitionPreviewer.previewTransition(request);
    }
}
