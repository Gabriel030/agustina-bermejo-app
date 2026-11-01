package com.red_escuela.backend.architecture.fixture.guard.controller;

import com.red_escuela.backend.shared.module.RequiresModule;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

public class MethodGuardedControllerFixture {

    @RequiresModule("billing")
    @GetMapping("/fixture")
    public void read() {
    }

    @RequiresModule("billing")
    @PostMapping("/fixture")
    public void write() {
    }
}
