package com.red_escuela.backend.graduation.procedure.specialist;

import com.red_escuela.backend.graduation.procedure.dto.StudentProcedureType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ProcedureEligibilityStrategies {

    private final Map<StudentProcedureType, ProcedureEligibilityStrategy> byType = new EnumMap<>(StudentProcedureType.class);

    public ProcedureEligibilityStrategies(List<ProcedureEligibilityStrategy> strategies) {
        for (ProcedureEligibilityStrategy strategy : strategies) {
            if (byType.putIfAbsent(strategy.type(), strategy) != null) {
                throw new IllegalStateException("Hay dos estrategias de elegibilidad para " + strategy.type());
            }
        }
    }

    public Optional<ProcedureEligibilityStrategy> forType(StudentProcedureType type) {
        return Optional.ofNullable(byType.get(type));
    }
}
