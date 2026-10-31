package com.red_escuela.backend.architecture.fixture.guard.controller;

import com.red_escuela.backend.platform.api.RequiresModule;
import org.springframework.web.bind.annotation.GetMapping;

@RequiresModule("billing")
public class ClassGuardedControllerFixture {

    @GetMapping("/fixture")
    public void handle() {
    }
}
