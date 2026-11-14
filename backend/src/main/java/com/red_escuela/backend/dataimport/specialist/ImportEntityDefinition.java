package com.red_escuela.backend.dataimport.specialist;

import com.red_escuela.backend.dataimport.domain.ImportEntityType;
import com.red_escuela.backend.dataimport.entity.ImportRowError;
import java.util.List;
import java.util.Map;

public interface ImportEntityDefinition {

    ImportEntityType type();

    List<String> columns();

    String naturalKey(Map<String, String> payload);

    List<ImportRowError> validateFormat(Map<String, String> payload);

    Map<String, String> uniquenessValues(Map<String, String> payload);

    BaseCheck checkAgainstBase(Map<String, String> payload);

    default List<ImportReference> references(Map<String, String> payload) {
        return List.of();
    }

    default boolean existsInBase(String naturalKey) {
        return false;
    }

    default Map<Integer, List<ImportRowError>> validateAcrossRows(List<Map<String, String>> payloads) {
        return Map.of();
    }

    Long record(Map<String, String> payload, Long institutionId);

    record BaseCheck(boolean keyExists, List<ImportRowError> errors) {
    }
}
