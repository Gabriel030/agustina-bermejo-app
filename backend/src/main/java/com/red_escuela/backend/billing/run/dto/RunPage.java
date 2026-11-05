package com.red_escuela.backend.billing.run.dto;

import java.util.List;

public record RunPage<T>(List<T> items, long totalElements) {
}
