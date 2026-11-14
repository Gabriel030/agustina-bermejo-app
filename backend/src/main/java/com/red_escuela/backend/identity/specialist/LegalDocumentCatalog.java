package com.red_escuela.backend.identity.specialist;

import com.red_escuela.backend.identity.dto.LegalDocumentResponse;
import com.red_escuela.backend.identity.entity.LegalDocumentType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LegalDocumentCatalog {

    private static final int VERSION_MAX_LENGTH = 40;

    private final List<LegalDocumentResponse> current;

    public LegalDocumentCatalog(@Value("${app.legal.terms-version}") String termsVersion,
                                @Value("${app.legal.privacy-version}") String privacyVersion) {
        this.current = List.of(
                new LegalDocumentResponse(LegalDocumentType.TERMS, validated(termsVersion), "/legal/terminos"),
                new LegalDocumentResponse(LegalDocumentType.PRIVACY, validated(privacyVersion), "/legal/privacidad"));
    }

    public List<LegalDocumentResponse> current() {
        return current;
    }

    private static String validated(String version) {
        if (version == null || version.isBlank() || version.trim().length() > VERSION_MAX_LENGTH) {
            throw new IllegalStateException("La versión de un documento legal debe tener entre 1 y " + VERSION_MAX_LENGTH + " caracteres");
        }
        return version.trim();
    }
}
