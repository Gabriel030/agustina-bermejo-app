package com.red_escuela.backend.academicstructure.dto;

import java.util.List;

public record StaffAssignmentsResponse(List<StaffAssignmentResponse> assignments, int totalWeeklyHours) {
}
