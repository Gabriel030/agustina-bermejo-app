package com.red_escuela.backend.evaluation.dto;

import java.util.List;

public record CommissionClosurePageResponse(List<CommissionClosureListItemResponse> items, int page, int size, long totalElements,
        CommissionClosureCountsResponse counts) {
}
