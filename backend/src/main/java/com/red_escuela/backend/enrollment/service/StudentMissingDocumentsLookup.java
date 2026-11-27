package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.api.StudentDocumentsStanding;
import com.red_escuela.backend.enrollment.api.StudentRequiredDocumentsFinder;
import com.red_escuela.backend.identity.api.StudentMissingDocumentsPort;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class StudentMissingDocumentsLookup implements StudentMissingDocumentsPort {

    private final StudentRequiredDocumentsFinder studentRequiredDocumentsFinder;

    public StudentMissingDocumentsLookup(StudentRequiredDocumentsFinder studentRequiredDocumentsFinder) {
        this.studentRequiredDocumentsFinder = studentRequiredDocumentsFinder;
    }

    @Override
    public Map<Long, List<String>> missingRequiredDocumentsByStudent(Collection<Long> studentIds) {
        Map<Long, List<String>> missing = new HashMap<>();
        for (Map.Entry<Long, StudentDocumentsStanding> entry : studentRequiredDocumentsFinder.findStandingOf(studentIds).entrySet()) {
            missing.put(entry.getKey(), List.copyOf(entry.getValue().missingDocuments()));
        }
        return missing;
    }
}
