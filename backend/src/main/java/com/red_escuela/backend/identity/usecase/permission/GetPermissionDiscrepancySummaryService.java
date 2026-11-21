package com.red_escuela.backend.identity.usecase.permission;

import com.red_escuela.backend.identity.dto.PermissionDiscrepancySummaryResponse;
import com.red_escuela.backend.identity.specialist.PermissionDiscrepancySummaryReader;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetPermissionDiscrepancySummaryService implements GetPermissionDiscrepancySummaryUseCase {

    private final PermissionDiscrepancySummaryReader reader;

    public GetPermissionDiscrepancySummaryService(PermissionDiscrepancySummaryReader reader) {
        this.reader = reader;
    }

    @Override
    public PermissionDiscrepancySummaryResponse get() {
        return reader.read();
    }
}
