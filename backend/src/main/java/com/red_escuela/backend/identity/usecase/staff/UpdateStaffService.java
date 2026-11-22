package com.red_escuela.backend.identity.usecase.staff;

import com.red_escuela.backend.identity.service.UpdateStaffTransaction;
import com.red_escuela.backend.identity.service.UpdatedStaff;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class UpdateStaffService implements UpdateStaffUseCase {
    private final UpdateStaffTransaction updateStaffTransaction;

    public UpdateStaffService(UpdateStaffTransaction updateStaffTransaction) {
        this.updateStaffTransaction = updateStaffTransaction;
    }

    @Override
    public StaffUseCaseResult update(UpdateStaffCommand command) {
        UpdatedStaff updated = updateStaffTransaction.update(command.personId(), command.request());
        return new StaffUseCaseResult(updated.person(), updated.role());
    }
}
