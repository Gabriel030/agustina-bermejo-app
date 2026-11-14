package com.red_escuela.backend.dataimport.specialist;

import com.red_escuela.backend.dataimport.domain.ImportEntityType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ImportDefinitionRegistry {
    private final Map<ImportEntityType, ImportEntityDefinition> definitions = new EnumMap<>(ImportEntityType.class);

    public ImportDefinitionRegistry(List<ImportEntityDefinition> available) {
        available.forEach(definition -> definitions.put(definition.type(), definition));
    }

    public ImportEntityDefinition definitionFor(ImportEntityType type) {
        return definitions.get(type);
    }

    public List<ImportEntityType> commitOrder() {
        return List.of(ImportEntityType.values());
    }
}
