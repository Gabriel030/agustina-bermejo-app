package com.red_escuela.backend.notification.broadcast.template.specialist;

import com.red_escuela.backend.identity.api.PersonTemplateFacts;
import com.red_escuela.backend.identity.api.PersonTemplateFactsFinder;
import com.red_escuela.backend.notification.broadcast.template.domain.TemplateVariable;
import com.red_escuela.backend.notification.broadcast.template.exception.TemplatePersonNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

@Component
public class PersonTemplateVariableSource implements TemplateVariableSource {

    private static final String PREVIEW_UNSUBSCRIBE_PATH = "/unsubscribe/vista-previa";

    private final PersonTemplateFactsFinder factsFinder;
    private final String frontendBaseUrl;

    public PersonTemplateVariableSource(PersonTemplateFactsFinder factsFinder,
                                        @Value("${app.frontend.base-url}") String frontendBaseUrl) {
        this.factsFinder = factsFinder;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @Override
    public Map<TemplateVariable, String> valuesFor(Long personId) {
        PersonTemplateFacts facts = factsFinder.findTemplateFacts(personId)
                .orElseThrow(() -> new TemplatePersonNotFoundException(personId));
        return valuesOf(facts);
    }

    @Override
    public Map<Long, Map<TemplateVariable, String>> valuesForAll(Collection<Long> personIds) {
        Map<Long, Map<TemplateVariable, String>> valuesByPerson = new HashMap<>();
        factsFinder.findTemplateFactsAll(personIds).forEach((personId, facts) -> valuesByPerson.put(personId, valuesOf(facts)));
        return valuesByPerson;
    }

    private Map<TemplateVariable, String> valuesOf(PersonTemplateFacts facts) {
        Map<TemplateVariable, String> values = new EnumMap<>(TemplateVariable.class);
        values.put(TemplateVariable.FIRST_NAME, facts.firstName());
        values.put(TemplateVariable.LAST_NAME, facts.lastName());
        values.put(TemplateVariable.INSTITUTION, facts.institutionName());
        values.put(TemplateVariable.UNSUBSCRIBE_LINK, frontendBaseUrl + PREVIEW_UNSUBSCRIBE_PATH);
        return values;
    }
}
