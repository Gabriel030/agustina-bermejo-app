package com.red_escuela.backend.architecture.fixture.guard.controller;

import org.springframework.web.bind.annotation.GetMapping;

public class UnguardedControllerFixture {

    @GetMapping("/fixture")
    public void handle() {
    }
}
