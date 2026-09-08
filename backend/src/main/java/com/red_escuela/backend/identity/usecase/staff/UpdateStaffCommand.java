package com.red_escuela.backend.identity.usecase.staff;

import com.red_escuela.backend.identity.dto.UpdateStaffRequest;

public record UpdateStaffCommand(Long personId, UpdateStaffRequest request) {
}
