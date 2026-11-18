package com.red_escuela.backend.dataimport.specialist;

import com.red_escuela.backend.dataimport.domain.ImportEntityType;
import com.red_escuela.backend.dataimport.entity.ImportRowError;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ImportEntityDefinition {

    ImportEntityType type();

    List<String> columns();

    String naturalKey(Map<String, String> payload);

    List<ImportRowError> validateFormat(Map<String, String> payload);

    Map<String, String> uniquenessValues(Map<String, String> payload);

    BaseCheck checkAgainstBase(Map<String, String> payload);

    default BaseLookups preloadBase(List<Map<String, String>> payloads) {
        return BaseLookups.NONE;
    }

    default BaseCheck checkAgainstBase(Map<String, String> payload, BaseLookups lookups) {
        return checkAgainstBase(payload);
    }

    default boolean existsInBase(String naturalKey, BaseLookups lookups) {
        return existsInBase(naturalKey);
    }

    default boolean aliasExistsInBase(String aliasKey, BaseLookups lookups) {
        return aliasExistsInBase(aliasKey);
    }

    default List<ImportReference> references(Map<String, String> payload) {
        return List.of();
    }

    default boolean existsInBase(String naturalKey) {
        return false;
    }

    default Map<Integer, List<ImportRowError>> validateAcrossRows(List<Map<String, String>> payloads) {
        return Map.of();
    }

    default Set<String> aliasKeys(Map<String, String> payload) {
        return Set.of();
    }

    default boolean aliasExistsInBase(String aliasKey) {
        return false;
    }

    Long record(Map<String, String> payload, Long institutionId);

    default BaseLookups preloadForRecord(List<Map<String, String>> payloads) {
        return BaseLookups.NONE;
    }

    default Long record(Map<String, String> payload, Long institutionId, BaseLookups lookups) {
        return record(payload, institutionId);
    }

    default void discard(Long entityId) {
        throw new UnsupportedOperationException("Esta estructura se retiene y no se deshace");
    }

    default boolean discardsInBatch() {
        return false;
    }

    default void discardBatch(List<Long> entityIds) {
        entityIds.forEach(this::discard);
    }

    default boolean parentMissing(Map<String, String> payload) {
        return false;
    }

    default Long parentIdOf(Long entityId) {
        throw new UnsupportedOperationException("Esta estructura no tiene registro padre");
    }

    default String parentNaturalKey(Map<String, String> payload) {
        throw new UnsupportedOperationException("Esta estructura no tiene registro padre");
    }

    default boolean isParentRecord(String naturalKey) {
        return false;
    }

    default void discardParent(Long parentId) {
        throw new UnsupportedOperationException("Esta estructura no tiene registro padre");
    }

    record BaseCheck(boolean keyExists, List<ImportRowError> errors) {
    }
}
