package com.red_escuela.backend.graduation.certificate.template.specialist;

import com.red_escuela.backend.graduation.certificate.template.dto.DocumentTemplateType;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class DocumentTemplateMarkers {

    private static final Set<String> COMMON = Set.of("holder", "documentType", "documentNumber", "issuedOn", "registryNumber");

    public Set<String> allowedFor(DocumentTemplateType type) {
        return switch (type) {
            case REGULAR_STUDENT_CERTIFICATE -> with("titleName", "recipient");
            case APPROVED_SUBJECTS_CERTIFICATE -> with("titleName", "average", "completionPercent");
            case EXAM_REGISTRATION_CERTIFICATE, EXAM_CERTIFICATE -> with("subjectName", "examDate", "recipient");
        };
    }

    private Set<String> with(String... extra) {
        Set<String> allowed = new HashSet<>(COMMON);
        allowed.addAll(Set.of(extra));
        return Set.copyOf(allowed);
    }
}
