package com.red_escuela.backend.evaluation.domain;

import java.util.function.BooleanSupplier;

public record ApprovalRequirementContext(BooleanSupplier prerequisitesMet) {
}
