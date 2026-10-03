package com.red_escuela.backend.enrollment.dto;

import jakarta.validation.constraints.Size;

public record DecideDirectorApprovalRequestRequest(@Size(max = 500) String notas) {
}
