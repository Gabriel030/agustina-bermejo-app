package com.red_escuela.backend.notification.broadcast.audience.domain;

import com.red_escuela.backend.notification.broadcast.template.domain.TemplateVariable;

import java.util.EnumSet;
import java.util.Set;

public final class MissingVariablePolicy {

    private static final Set<TemplateVariable> COURSE_VARIABLES =
            EnumSet.of(TemplateVariable.PROGRAM, TemplateVariable.SUBJECT, TemplateVariable.SECTION);

    private MissingVariablePolicy() {
    }

    public static boolean referencesCourseVariable(Set<TemplateVariable> referenced) {
        return referenced.stream().anyMatch(COURSE_VARIABLES::contains);
    }

    public static boolean isMissingAnyVariable(Set<TemplateVariable> referenced, String firstName, String lastName,
                                               boolean institutionNameConfigured,
                                               boolean hasCurrentCourse) {
        return referenced.stream().anyMatch(variable -> isMissing(variable, firstName, lastName, institutionNameConfigured,
                hasCurrentCourse));
    }

    private static boolean isMissing(TemplateVariable variable, String firstName, String lastName,
                                     boolean institutionNameConfigured, boolean hasCurrentCourse) {
        if (COURSE_VARIABLES.contains(variable)) {
            return !hasCurrentCourse;
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
