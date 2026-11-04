package com.red_escuela.backend.academicstructure.usecase.staffassignment;

import com.red_escuela.backend.academicstructure.dto.StaffAssignmentsResponse;
import com.red_escuela.backend.academicstructure.service.StaffAssignmentLister;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ListStaffAssignmentsService implements ListStaffAssignmentsUseCase {

    private final StaffAssignmentLister staffAssignmentLister;

    public ListStaffAssignmentsService(StaffAssignmentLister staffAssignmentLister) {
        this.staffAssignmentLister = staffAssignmentLister;
    }

    @Override
    public StaffAssignmentsResponse list(Long staffPersonId, boolean includeClosed) {
        return staffAssignmentLister.list(staffPersonId, includeClosed);
    }
}
