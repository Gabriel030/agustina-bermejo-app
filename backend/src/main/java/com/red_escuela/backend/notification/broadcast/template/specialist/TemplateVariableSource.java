package com.red_escuela.backend.notification.broadcast.template.specialist;

import com.red_escuela.backend.notification.broadcast.template.domain.TemplateVariable;

import java.util.Map;

public interface TemplateVariableSource {
    Map<TemplateVariable, String> valuesFor(Long personId);
}
