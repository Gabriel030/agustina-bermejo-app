package com.red_escuela.backend.identity.dto;

import com.red_escuela.backend.identity.entity.WithdrawalReason;
import jakarta.validation.constraints.NotNull;

public record WithdrawStudentRequest(@NotNull WithdrawalReason reason, String detail) {
}
