package com.red_escuela.backend.identity.usecase.student;

import com.red_escuela.backend.identity.dto.WithdrawStudentRequest;

public record WithdrawStudentCommand(Long studentId, WithdrawStudentRequest request) {
}
