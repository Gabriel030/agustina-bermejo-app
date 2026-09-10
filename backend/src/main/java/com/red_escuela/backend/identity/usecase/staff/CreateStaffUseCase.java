package com.red_escuela.backend.identity.usecase.staff;

import com.red_escuela.backend.identity.dto.CreateStaffRequest;

public interface CreateStaffUseCase {
    StaffUseCaseResult create(CreateStaffRequest request);
}
