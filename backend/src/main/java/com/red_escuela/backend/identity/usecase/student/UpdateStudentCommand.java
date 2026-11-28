package com.red_escuela.backend.identity.usecase.student;

import com.red_escuela.backend.identity.dto.UpdateStudentRequest;

public record UpdateStudentCommand(Long studentId, UpdateStudentRequest request, boolean includeWithdrawalReason) {
}
