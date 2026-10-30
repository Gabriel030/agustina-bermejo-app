package com.red_escuela.backend.enrollment.api;

public record ElectiveGroupProgressView(
        Long electiveGroupId, String electiveGroupName,
        int approvedCount, int minRequired, Integer maxAllowed, boolean fulfilled) {
}
