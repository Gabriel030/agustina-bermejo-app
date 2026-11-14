package com.red_escuela.backend.dataimport.specialist;

import com.red_escuela.backend.dataimport.domain.ImportEntityType;

public record ImportReference(ImportEntityType type, String naturalKey, String column, String code, boolean prefix,
        boolean alias) {
    public ImportReference(ImportEntityType type, String naturalKey, String column, String code) {
        this(type, naturalKey, column, code, false, false);
    }

    public static ImportReference anyWithPrefix(ImportEntityType type, String keyPrefix, String column, String code) {
        return new ImportReference(type, keyPrefix, column, code, true, false);
    }

    public static ImportReference byAlias(ImportEntityType type, String aliasKey, String column, String code) {
        return new ImportReference(type, aliasKey, column, code, false, true);
    }
}
