package com.red_escuela.backend.identity.usecase.student;

import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;

public record ListStudentsCommand(StatusType status, StudentOnboardingStatus onboardingStatus, String search) {
}
