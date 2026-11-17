package com.red_escuela.backend.notification.broadcast.template.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public final class TemplateVariableCatalog {

    private TemplateVariableCatalog() {
    }

    public static List<TemplateVariable> allVariables() {
        return List.of(TemplateVariable.values());
    }

    public static Optional<TemplateVariable> findByToken(String token) {
        return Arrays.stream(TemplateVariable.values()).filter(variable -> variable.token().equals(token)).findFirst();
    }
}
