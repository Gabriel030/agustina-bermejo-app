package com.red_escuela.backend.identity.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface StudentMissingDocumentsPort {
    Map<Long, List<String>> missingRequiredDocumentsByStudent(Collection<Long> studentIds);
}
