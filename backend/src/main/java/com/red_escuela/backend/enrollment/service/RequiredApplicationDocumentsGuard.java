package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.entity.DocumentType;
import com.red_escuela.backend.enrollment.exception.ApplicationDocumentTooLargeException;
import com.red_escuela.backend.enrollment.exception.RequiredApplicationDocumentsMissingException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Service
public class RequiredApplicationDocumentsGuard {

    public static final long MAX_DOCUMENT_BYTES = 3L * 1024 * 1024;

    public void requireAllFilesPresent(Map<DocumentType, MultipartFile> documents) {
        Set<DocumentType> missing = EnumSet.allOf(DocumentType.class);
        documents.forEach((type, file) -> {
            if (file != null && !file.isEmpty()) {
                missing.remove(type);
            }
        });
        if (!missing.isEmpty()) {
            throw new RequiredApplicationDocumentsMissingException(namesOf(missing));
        }
        documents.forEach((type, file) -> {
            if (file.getSize() > MAX_DOCUMENT_BYTES) {
                throw new ApplicationDocumentTooLargeException(type.name());
            }
        });
    }

    private Set<String> namesOf(Set<DocumentType> types) {
        return types.stream().map(Enum::name).collect(Collectors.toCollection(TreeSet::new));
    }
}
