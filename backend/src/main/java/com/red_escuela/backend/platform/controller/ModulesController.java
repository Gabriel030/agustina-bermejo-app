package com.red_escuela.backend.platform.controller;

import com.red_escuela.backend.platform.dto.MyModulesResponse;
import com.red_escuela.backend.platform.dto.PublicModulesResponse;
import com.red_escuela.backend.platform.usecase.modules.GetMyModulesUseCase;
import com.red_escuela.backend.platform.usecase.modules.GetPublicModulesUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ModulesController {

    private final GetMyModulesUseCase getMyModules;
    private final GetPublicModulesUseCase getPublicModules;

    public ModulesController(GetMyModulesUseCase getMyModules, GetPublicModulesUseCase getPublicModules) {
        this.getMyModules = getMyModules;
        this.getPublicModules = getPublicModules;
    }

    @GetMapping("/me/modules")
    public MyModulesResponse myModules() {
        return MyModulesResponse.from(getMyModules.read());
    }

    @GetMapping("/public/modules")
    public PublicModulesResponse publicModules() {
        return PublicModulesResponse.from(getPublicModules.read());
    }
}
