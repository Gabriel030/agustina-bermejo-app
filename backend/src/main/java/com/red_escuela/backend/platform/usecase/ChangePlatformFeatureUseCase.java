package com.red_escuela.backend.platform.usecase;

import com.red_escuela.backend.platform.domain.FeatureChange;
import com.red_escuela.backend.platform.domain.FeatureState;

public interface ChangePlatformFeatureUseCase {
    FeatureState change(FeatureChange change);
}
