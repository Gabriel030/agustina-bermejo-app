package com.red_escuela.backend.dataimport.specialist;

import com.red_escuela.backend.dataimport.domain.ImportEntityType;

public record ImportReference(ImportEntityType type, String naturalKey, String column, String code) {
}
