package com.red_escuela.backend.identity.usecase.student;

import com.red_escuela.backend.shared.domain.StatusType;

public record ListStudentsCommand(StatusType status, String onboardingStatus, String search,
                                  int page, int size, boolean includeWithdrawalReason) {
}
