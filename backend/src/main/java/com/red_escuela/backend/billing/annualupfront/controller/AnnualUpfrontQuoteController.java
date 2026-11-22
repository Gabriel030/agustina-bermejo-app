package com.red_escuela.backend.billing.annualupfront.controller;

import com.red_escuela.backend.shared.permission.RequiresPermission;
import com.red_escuela.backend.billing.annualupfront.dto.AnnualUpfrontQuoteResponse;
import com.red_escuela.backend.billing.annualupfront.usecase.QuoteAnnualUpfrontUseCase;
import com.red_escuela.backend.shared.module.RequiresModule;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiresModule(RequiresModule.BILLING)
public class AnnualUpfrontQuoteController {
    private final QuoteAnnualUpfrontUseCase quoteUseCase;

    public AnnualUpfrontQuoteController(QuoteAnnualUpfrontUseCase quoteUseCase) {
        this.quoteUseCase = quoteUseCase;
    }

    @GetMapping("/me/program-enrollments/{id}/annual-upfront/quote")
    public AnnualUpfrontQuoteResponse quoteForCurrentStudent(@PathVariable Long id) {
        return AnnualUpfrontQuoteResponse.from(quoteUseCase.quoteAnnualUpfrontForCurrentStudent(id));
    }

    @GetMapping("/program-enrollments/{id}/annual-upfront/quote")
    @RequiresPermission(code = "billing.read_annual_upfront", legacyRoles = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public AnnualUpfrontQuoteResponse quoteForStaff(@PathVariable Long id) {
        return AnnualUpfrontQuoteResponse.from(quoteUseCase.quoteAnnualUpfrontForStaff(id));
    }
}
