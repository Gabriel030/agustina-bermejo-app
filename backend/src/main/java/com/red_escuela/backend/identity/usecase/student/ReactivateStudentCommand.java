package com.red_escuela.backend.identity.usecase.student;

import com.red_escuela.backend.identity.dto.ReactivateStudentRequest;

public record ReactivateStudentCommand(Long studentId, ReactivateStudentRequest request) {
}
