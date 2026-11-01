package com.red_escuela.backend.platform.usecase;

import com.red_escuela.backend.platform.domain.FeatureTransitionPreview;
import com.red_escuela.backend.platform.domain.FeatureTransitionRequest;

public interface PreviewPlatformFeatureChangeUseCase {
    FeatureTransitionPreview preview(FeatureTransitionRequest request);
}
