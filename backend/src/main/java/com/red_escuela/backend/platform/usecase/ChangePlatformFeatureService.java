package com.red_escuela.backend.platform.usecase;

import com.red_escuela.backend.platform.domain.FeatureChange;
import com.red_escuela.backend.platform.domain.FeatureState;
import com.red_escuela.backend.platform.service.PlatformFeatureChanger;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ChangePlatformFeatureService implements ChangePlatformFeatureUseCase {

    private final PlatformFeatureChanger featureChanger;

    public ChangePlatformFeatureService(PlatformFeatureChanger featureChanger) {
        this.featureChanger = featureChanger;
    }

    @Override
    public FeatureState change(FeatureChange change) {
        return featureChanger.changeFeature(change);
    }
}
