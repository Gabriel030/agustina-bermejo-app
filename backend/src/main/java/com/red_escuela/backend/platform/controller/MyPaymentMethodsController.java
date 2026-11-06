package com.red_escuela.backend.platform.controller;

import com.red_escuela.backend.platform.dto.MyPaymentMethodResponse;
import com.red_escuela.backend.platform.usecase.settings.GetMyPaymentMethodsUseCase;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class MyPaymentMethodsController {

    private final GetMyPaymentMethodsUseCase getMyPaymentMethods;

    public MyPaymentMethodsController(GetMyPaymentMethodsUseCase getMyPaymentMethods) {
        this.getMyPaymentMethods = getMyPaymentMethods;
    }

    @GetMapping("/me/payment-methods")
    @PreAuthorize("hasAnyRole('ALUMNO','DOCENTE','PRECEPTOR','ADMINISTRATIVO','DIRECTOR')")
    public List<MyPaymentMethodResponse> myPaymentMethods() {
        return getMyPaymentMethods.read().stream().map(MyPaymentMethodResponse::of).toList();
    }
}
