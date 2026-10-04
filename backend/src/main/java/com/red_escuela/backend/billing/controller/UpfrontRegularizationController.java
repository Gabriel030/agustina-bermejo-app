package com.red_escuela.backend.billing.controller;

import com.red_escuela.backend.billing.service.UpfrontRegularizationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class UpfrontRegularizationController {
    private final UpfrontRegularizationService regularizationService;

    public UpfrontRegularizationController(UpfrontRegularizationService regularizationService) {
        this.regularizationService = regularizationService;
    }

    @PostMapping("/billing/upfront-regularization")
    @PreAuthorize("hasRole('DIRECTOR')")
    public List<UpfrontRegularizationService.Candidate> run(@RequestParam(defaultValue = "true") boolean dryRun) {
        return dryRun ? regularizationService.dryRun() : regularizationService.regularize();
    }
}
