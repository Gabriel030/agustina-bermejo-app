package com.red_escuela.backend.platform.controller;

import com.red_escuela.backend.platform.dto.MyPaymentMethodResponse;
import com.red_escuela.backend.platform.usecase.settings.GetMyPaymentMethodsUseCase;
import com.red_escuela.backend.shared.permission.RequiresPermission;
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
    @RequiresPermission(code = "platform.read_own_payment_methods", legacyRoles = "ROLE_ALUMNO,ROLE_DOCENTE,ROLE_PRECEPTOR,ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public List<MyPaymentMethodResponse> myPaymentMethods() {
        return getMyPaymentMethods.read().stream().map(MyPaymentMethodResponse::of).toList();
    }
}
