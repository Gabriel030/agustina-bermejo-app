package com.red_escuela.backend.dto;

import com.red_escuela.backend.entity.WithdrawalReason;
import jakarta.validation.constraints.NotNull;

public record WithdrawStudentRequest(@NotNull WithdrawalReason reason, String detail) {
}
