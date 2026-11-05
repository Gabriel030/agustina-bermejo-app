package com.red_escuela.backend.identity.usecase.staff;

import com.red_escuela.backend.identity.service.StaffReactivation;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ReactivateStaffService implements ReactivateStaffUseCase {

    private final StaffReactivation staffReactivation;

    public ReactivateStaffService(StaffReactivation staffReactivation) {
        this.staffReactivation = staffReactivation;
    }

    @Override
    public void reactivate(Long personId) {
        staffReactivation.reactivate(personId);
    }
}
