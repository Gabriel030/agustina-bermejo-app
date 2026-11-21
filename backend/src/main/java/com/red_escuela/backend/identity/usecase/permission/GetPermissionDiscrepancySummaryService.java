package com.red_escuela.backend.identity.usecase.permission;

import com.red_escuela.backend.identity.dto.PermissionDiscrepancySummaryResponse;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class GetPermissionDiscrepancySummaryService implements GetPermissionDiscrepancySummaryUseCase {

    @Override
    public PermissionDiscrepancySummaryResponse get() {
        return new PermissionDiscrepancySummaryResponse(List.of());
    }
}
