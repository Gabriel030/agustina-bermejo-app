package com.red_escuela.backend.billing.catalog;

import java.util.List;
import java.util.Map;

public record OperationCatalogEntry(String type, List<String> validationMoments, List<String> preHandlers, List<String> events,
                                    Map<String, List<String>> listenersByEvent) {
}
