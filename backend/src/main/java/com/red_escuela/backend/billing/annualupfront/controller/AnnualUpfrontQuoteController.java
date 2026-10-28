package com.red_escuela.backend.billing.annualupfront.controller;

import com.red_escuela.backend.billing.annualupfront.dto.AnnualUpfrontQuoteResponse;
import com.red_escuela.backend.billing.annualupfront.usecase.QuoteAnnualUpfrontUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
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
    public AnnualUpfrontQuoteResponse quoteForStaff(@PathVariable Long id) {
        return AnnualUpfrontQuoteResponse.from(quoteUseCase.quoteAnnualUpfrontForStaff(id));
    }
}
