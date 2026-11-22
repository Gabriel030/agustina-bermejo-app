package com.red_escuela.backend.identity.usecase.staff;

import com.red_escuela.backend.identity.dto.CreateStaffRequest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.service.CreateStaffTransaction;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class CreateStaffService implements CreateStaffUseCase {
    private final CreateStaffTransaction createStaffTransaction;

    public CreateStaffService(CreateStaffTransaction createStaffTransaction) {
        this.createStaffTransaction = createStaffTransaction;
    }

    @Override
    public StaffUseCaseResult create(CreateStaffRequest request) {
        Person person = createStaffTransaction.create(request);
        return new StaffUseCaseResult(person, request.role());
    }
}
