package com.red_escuela.backend.notification.broadcast.template.specialist;

import com.red_escuela.backend.enrollment.api.PersonCurrentCourse;
import com.red_escuela.backend.enrollment.api.PersonCurrentCourseFinder;
import com.red_escuela.backend.identity.api.PersonTemplateFacts;
import com.red_escuela.backend.identity.api.PersonTemplateFactsFinder;
import com.red_escuela.backend.notification.broadcast.template.domain.TemplateVariable;
import com.red_escuela.backend.notification.broadcast.template.exception.TemplatePersonNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class PersonTemplateVariableSource implements TemplateVariableSource {

    private static final String PREVIEW_UNSUBSCRIBE_PATH = "/unsubscribe/vista-previa";

    private final PersonTemplateFactsFinder factsFinder;
    private final PersonCurrentCourseFinder currentCourseFinder;
    private final String frontendBaseUrl;

    public PersonTemplateVariableSource(PersonTemplateFactsFinder factsFinder,
                                        PersonCurrentCourseFinder currentCourseFinder,
                                        @Value("${app.frontend.base-url}") String frontendBaseUrl) {
        this.factsFinder = factsFinder;
        this.currentCourseFinder = currentCourseFinder;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @Override
    public Map<TemplateVariable, String> valuesFor(Long personId) {
        PersonTemplateFacts facts = factsFinder.findTemplateFacts(personId)
                .orElseThrow(() -> new TemplatePersonNotFoundException(personId));
        return valuesOf(facts, currentCourseFinder.findMostRecentCurrentCourses(List.of(personId)).get(personId));
    }

    @Override
    public Map<Long, Map<TemplateVariable, String>> valuesForAll(Collection<Long> personIds) {
        Map<Long, Map<TemplateVariable, String>> valuesByPerson = new HashMap<>();
        Map<Long, PersonCurrentCourse> coursesByPerson = currentCourseFinder.findMostRecentCurrentCourses(personIds);
        factsFinder.findTemplateFactsAll(personIds).forEach((personId, facts) ->
                valuesByPerson.put(personId, valuesOf(facts, coursesByPerson.get(personId))));
        return valuesByPerson;
    }

    private Map<TemplateVariable, String> valuesOf(PersonTemplateFacts facts, PersonCurrentCourse course) {
        Map<TemplateVariable, String> values = new EnumMap<>(TemplateVariable.class);
        values.put(TemplateVariable.FIRST_NAME, facts.firstName());
        values.put(TemplateVariable.LAST_NAME, facts.lastName());
        values.put(TemplateVariable.INSTITUTION, facts.institutionName());
        if (course != null) {
            values.put(TemplateVariable.PROGRAM, course.programName());
            values.put(TemplateVariable.SUBJECT, course.subjectName());
            values.put(TemplateVariable.SECTION, course.sectionName());
        }
        values.put(TemplateVariable.UNSUBSCRIBE_LINK, frontendBaseUrl + PREVIEW_UNSUBSCRIBE_PATH);
        return values;
    }
}
