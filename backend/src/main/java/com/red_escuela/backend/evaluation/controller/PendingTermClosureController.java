package com.red_escuela.backend.evaluation.controller;

import com.red_escuela.backend.evaluation.dto.PendingTermClosuresResponse;
import com.red_escuela.backend.evaluation.usecase.closure.ListPendingTermClosuresUseCase;
import com.red_escuela.backend.shared.permission.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PendingTermClosureController {

    private final ListPendingTermClosuresUseCase listPendingTermClosuresUseCase;

    public PendingTermClosureController(ListPendingTermClosuresUseCase listPendingTermClosuresUseCase) {
        this.listPendingTermClosuresUseCase = listPendingTermClosuresUseCase;
    }

    @GetMapping("/commission-closures/pending-term-closures")
    @RequiresPermission(code = "evaluation.read_commission_closures", legacyRoles = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public PendingTermClosuresResponse listPendingTermClosures() {
        return listPendingTermClosuresUseCase.listPendingTermClosures();
    }
}
