package com.red_escuela.backend.notification.broadcast.template.exception;

import com.red_escuela.backend.shared.exception.ReportsErrorDetails;
import com.red_escuela.backend.shared.exception.UnprocessableEntityException;

import java.util.Map;

public class UnknownTemplateVariableException extends UnprocessableEntityException implements ReportsErrorDetails {

    private final String variable;

    public UnknownTemplateVariableException(String variable) {
        super("Unknown template variable");
        this.variable = variable;
    }

    @Override
    public Map<String, Object> details() {
        return variable == null ? Map.of() : Map.of("variable", variable);
    }
}
