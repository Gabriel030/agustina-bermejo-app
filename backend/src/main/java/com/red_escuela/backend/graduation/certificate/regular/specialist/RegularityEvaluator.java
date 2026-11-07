package com.red_escuela.backend.graduation.certificate.regular.specialist;

import com.red_escuela.backend.graduation.certificate.regular.dto.RegularityVerdict;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class RegularityEvaluator {

    private final Map<RegularityKind, RegularityRule> rulesByKind = new EnumMap<>(RegularityKind.class);

    public RegularityEvaluator(List<RegularityRule> rules) {
        rules.forEach(rule -> rulesByKind.put(rule.kind(), rule));
    }

    public RegularityVerdict evaluate(RegularityKind kind, Long enrollmentId) {
        return rulesByKind.get(kind).evaluate(enrollmentId);
    }
}
