package com.red_escuela.backend.notification.broadcast.audience.domain;

import com.red_escuela.backend.notification.broadcast.template.domain.TemplateVariable;

import java.util.EnumSet;
import java.util.Set;

public final class MissingVariablePolicy {

    private static final Set<TemplateVariable> WITHOUT_PERSON_SOURCE =
            EnumSet.of(TemplateVariable.PROGRAM, TemplateVariable.SUBJECT, TemplateVariable.SECTION);

    private MissingVariablePolicy() {
    }

    public static boolean isMissingAnyVariable(Set<TemplateVariable> referenced, String firstName, String lastName,
                                               boolean institutionNameConfigured) {
        return referenced.stream().anyMatch(variable -> isMissing(variable, firstName, lastName, institutionNameConfigured));
    }

    private static boolean isMissing(TemplateVariable variable, String firstName, String lastName,
                                     boolean institutionNameConfigured) {
        if (WITHOUT_PERSON_SOURCE.contains(variable)) {
            return true;
        }
        return switch (variable) {
            case FIRST_NAME -> isBlank(firstName);
            case LAST_NAME -> isBlank(lastName);
            case INSTITUTION -> !institutionNameConfigured;
            default -> false;
        };
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
