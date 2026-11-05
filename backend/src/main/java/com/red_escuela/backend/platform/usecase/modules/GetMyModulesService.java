package com.red_escuela.backend.platform.usecase.modules;

import com.red_escuela.backend.platform.domain.EffectiveModules;
import com.red_escuela.backend.platform.service.EffectiveModulesReader;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetMyModulesService implements GetMyModulesUseCase {

    private final EffectiveModulesReader modulesReader;

    public GetMyModulesService(EffectiveModulesReader modulesReader) {
        this.modulesReader = modulesReader;
    }

    @Override
    public EffectiveModules read() {
        return modulesReader.readEffectiveModules();
    }
}
