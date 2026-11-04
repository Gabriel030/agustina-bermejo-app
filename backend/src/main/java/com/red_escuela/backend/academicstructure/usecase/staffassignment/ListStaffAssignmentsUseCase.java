package com.red_escuela.backend.academicstructure.usecase.staffassignment;

import com.red_escuela.backend.academicstructure.dto.StaffAssignmentsResponse;

public interface ListStaffAssignmentsUseCase {
    StaffAssignmentsResponse list(Long staffPersonId, boolean includeClosed);
}
