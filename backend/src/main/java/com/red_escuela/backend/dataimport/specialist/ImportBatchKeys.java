package com.red_escuela.backend.dataimport.specialist;

import com.red_escuela.backend.dataimport.domain.ImportEntityType;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;

final class ImportBatchKeys {
    private final Map<ImportEntityType, NavigableSet<String>> naturalKeys;
    private final Map<ImportEntityType, Set<String>> aliasKeys;

    ImportBatchKeys(Map<ImportEntityType, NavigableSet<String>> naturalKeys, Map<ImportEntityType, Set<String>> aliasKeys) {
        this.naturalKeys = Map.copyOf(naturalKeys);
        this.aliasKeys = Map.copyOf(aliasKeys);
    }

    boolean containsNaturalKey(ImportEntityType type, String naturalKey) {
        return naturalKeys.get(type).contains(naturalKey);
    }

    int countStartingWith(ImportEntityType type, String prefix, int limit) {
        int count = 0;
        for (String key : naturalKeys.get(type).tailSet(prefix, true)) {
            if (!key.startsWith(prefix) || count == limit) {
                break;
            }
            count++;
        }
        return count;
    }

    boolean containsAlias(ImportEntityType type, String aliasKey) {
        return aliasKeys.get(type).contains(aliasKey);
    }
}
