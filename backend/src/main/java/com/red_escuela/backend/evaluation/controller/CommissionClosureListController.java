package com.red_escuela.backend.evaluation.controller;

import com.red_escuela.backend.evaluation.domain.CommissionClosureListFilter;
import com.red_escuela.backend.evaluation.dto.CommissionClosurePageResponse;
import com.red_escuela.backend.evaluation.entity.ClosureStatus;
import com.red_escuela.backend.evaluation.usecase.closure.ListCommissionClosuresCommand;
import com.red_escuela.backend.evaluation.usecase.closure.ListCommissionClosuresUseCase;
import com.red_escuela.backend.shared.permission.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CommissionClosureListController {

    private final ListCommissionClosuresUseCase listCommissionClosuresUseCase;

    public CommissionClosureListController(ListCommissionClosuresUseCase listCommissionClosuresUseCase) {
        this.listCommissionClosuresUseCase = listCommissionClosuresUseCase;
    }

    @GetMapping("/commission-closures")
    @RequiresPermission(code = "evaluation.read_commission_closures", legacyRoles = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public CommissionClosurePageResponse listCommissionClosures(@RequestParam(required = false) ClosureStatus status,
            @RequestParam(required = false) Long termId, @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return listCommissionClosuresUseCase.list(new ListCommissionClosuresCommand(new CommissionClosureListFilter(status, termId, q), page, size));
    }
}
