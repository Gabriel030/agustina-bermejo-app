package com.red_escuela.backend.billing.configuration.domain;

import com.red_escuela.backend.academicstructure.domain.TermType;

import java.util.List;

public record IrregularTermTypes(List<TermType> types) implements ConfigurationValues {

    public IrregularTermTypes {
        if (types == null) {
            throw new InvalidConfigurationValuesException("La lista de tipos de período irregular es obligatoria");
        }
        if (types.stream().anyMatch(type -> type == null)) {
            throw new InvalidConfigurationValuesException("Los tipos de período irregular no pueden estar vacíos");
        }
        types = types.stream().distinct().sorted().toList();
    }

    @Override
    public ConfigurationScope scope() {
        return ConfigurationScope.IRREGULAR_TERMS;
    }
}
