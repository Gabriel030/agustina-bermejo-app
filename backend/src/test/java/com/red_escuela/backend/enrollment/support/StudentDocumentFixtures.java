package com.red_escuela.backend.enrollment.support;

import com.red_escuela.backend.enrollment.entity.DocumentType;
import com.red_escuela.backend.enrollment.entity.StudentDocument;
import com.red_escuela.backend.identity.entity.Student;
import jakarta.persistence.EntityManager;

import java.time.LocalDateTime;

public final class StudentDocumentFixtures {

    private StudentDocumentFixtures() {
    }

    public static void persistAllRequiredDocuments(EntityManager entityManager, Student student) {
        for (DocumentType type : DocumentType.values()) {
            StudentDocument document = new StudentDocument();
            document.setStudent(student);
            document.setType(type);
            document.setFilePath("students/" + student.getId() + "/" + type.name() + "_fixture.pdf");
            document.setUploadedAt(LocalDateTime.now());
            entityManager.persist(document);
        }
    }
}
