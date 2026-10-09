package com.red_escuela.backend.billing.collections.operation;

import java.util.List;
import java.util.Map;

public record OverdueDetectionResult(int candidates, int marked, int failed, List<Map<String, Object>> errors) {
}
