package com.red_escuela.backend.platform.usecase.settings;

import com.red_escuela.backend.platform.domain.MyPaymentMethod;

import java.util.List;

public interface GetMyPaymentMethodsUseCase {
    List<MyPaymentMethod> read();
}
