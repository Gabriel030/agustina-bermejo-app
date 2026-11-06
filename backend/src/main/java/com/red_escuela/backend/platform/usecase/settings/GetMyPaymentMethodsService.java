package com.red_escuela.backend.platform.usecase.settings;

import com.red_escuela.backend.platform.domain.MyPaymentMethod;
import com.red_escuela.backend.platform.service.MyPaymentMethodsReader;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class GetMyPaymentMethodsService implements GetMyPaymentMethodsUseCase {

    private final MyPaymentMethodsReader paymentMethodsReader;

    public GetMyPaymentMethodsService(MyPaymentMethodsReader paymentMethodsReader) {
        this.paymentMethodsReader = paymentMethodsReader;
    }

    @Override
    public List<MyPaymentMethod> read() {
        return paymentMethodsReader.readAll();
    }
}
