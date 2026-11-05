package com.red_escuela.backend.platform.usecase;

import com.red_escuela.backend.platform.domain.FeatureDetail;
import com.red_escuela.backend.platform.service.FeatureDetailReader;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ListPlatformFeaturesService implements ListPlatformFeaturesUseCase {

    private final FeatureDetailReader featureDetailReader;

    public ListPlatformFeaturesService(FeatureDetailReader featureDetailReader) {
        this.featureDetailReader = featureDetailReader;
    }

    @Override
    public List<FeatureDetail> list() {
        return featureDetailReader.readAll();
    }
}
