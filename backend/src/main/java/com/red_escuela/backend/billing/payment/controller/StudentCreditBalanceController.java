package com.red_escuela.backend.billing.payment.controller;

import com.red_escuela.backend.billing.payment.dto.StudentCreditBalanceResponse;
import com.red_escuela.backend.billing.payment.usecase.DescribeStudentCreditBalanceUseCase;
import com.red_escuela.backend.identity.service.CurrentStudentService;
import com.red_escuela.backend.shared.domain.Currency;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StudentCreditBalanceController {
    private static final String CURRENCY_DEFAULT = "ARS";

    private final DescribeStudentCreditBalanceUseCase describeCreditBalanceUseCase;
    private final CurrentStudentService currentStudentService;

    public StudentCreditBalanceController(DescribeStudentCreditBalanceUseCase describeCreditBalanceUseCase, CurrentStudentService currentStudentService) {
        this.describeCreditBalanceUseCase = describeCreditBalanceUseCase;
        this.currentStudentService = currentStudentService;
    }

    @GetMapping("/me/credit-balance")
    @PreAuthorize("hasRole('ALUMNO')")
    public StudentCreditBalanceResponse describeOwnCreditBalance(@RequestParam(name = "currency", defaultValue = CURRENCY_DEFAULT) Currency currency) {
        return StudentCreditBalanceResponse.from(describeCreditBalanceUseCase.describeOwnCreditBalance(currentStudentService.resolveCurrentStudent().getId(), currency));
    }

    @GetMapping("/students/{studentId}/credit-balance")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    @ApiResponses({@ApiResponse(responseCode = "404", description = "STUDENT_NOT_FOUND: el alumno no existe")})
    public StudentCreditBalanceResponse describeCreditBalanceOfStudent(@PathVariable Long studentId,
                                                                       @RequestParam(name = "currency", defaultValue = CURRENCY_DEFAULT) Currency currency) {
        return StudentCreditBalanceResponse.from(describeCreditBalanceUseCase.describeCreditBalanceOfStudent(studentId, currency));
    }
}
