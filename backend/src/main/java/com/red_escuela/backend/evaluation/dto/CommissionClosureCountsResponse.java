package com.red_escuela.backend.evaluation.dto;

public record CommissionClosureCountsResponse(long all, long teacherConfirmed, long draftAvailable, long closed, long reopened) {
}
