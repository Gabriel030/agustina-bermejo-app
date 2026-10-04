package com.red_escuela.backend.identity.usecase.staff;

import com.red_escuela.backend.shared.domain.StatusType;

public record ListStaffCommand(String roleFilter, StatusType status, String search) {
}
