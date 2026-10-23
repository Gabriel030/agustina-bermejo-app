package com.red_escuela.backend.billing.collections.dto;

import java.util.List;

public record OverdueContactPageResponse(List<OverdueContactResponse> items, int page, int size, long totalElements) {
}
