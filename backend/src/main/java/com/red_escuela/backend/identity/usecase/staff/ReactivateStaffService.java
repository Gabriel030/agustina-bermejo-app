package com.red_escuela.backend.identity.usecase.staff;

import com.red_escuela.backend.identity.specialist.StaffReactivator;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ReactivateStaffService implements ReactivateStaffUseCase {

    private final StaffReactivator staffReactivation;

    public ReactivateStaffService(StaffReactivator staffReactivation) {
        this.staffReactivation = staffReactivation;
    }

    @Override
    public void reactivate(Long personId) {
        staffReactivation.reactivate(personId);
    }
}
