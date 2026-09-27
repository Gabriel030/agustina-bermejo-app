package com.red_escuela.backend.announcement.service.audience;

import com.red_escuela.backend.announcement.entity.AudienceQuantifier;

import java.util.List;
import java.util.function.IntPredicate;

public final class Quantifiers {

    private Quantifiers() {
    }

    public static boolean holds(List<Integer> instances, AudienceQuantifier quantifier, IntPredicate predicate) {
        if (instances.isEmpty()) {
            return false;
        }
        return quantifier == AudienceQuantifier.ALL
                ? instances.stream().allMatch(predicate::test)
                : instances.stream().anyMatch(predicate::test);
    }
}
