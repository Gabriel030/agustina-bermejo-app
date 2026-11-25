package com.red_escuela.backend.graduation.certificate.template.specialist;

import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateContent;
import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateSigner;
import com.red_escuela.backend.graduation.certificate.template.exception.TemplateIncompleteException;
import com.red_escuela.backend.graduation.certificate.template.exception.TemplateSignatureMissingException;
import com.red_escuela.backend.graduation.certificate.template.exception.TemplateUnknownMarkerException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Component
public class CertificateTemplateContentRules {

    static final Set<String> ALLOWED_MARKERS = Set.of("holder", "documentType", "documentNumber", "titleKind", "titleName", "hours",
            "graduationDate", "issuedOn", "registryNumber");

    private static final Pattern MARKER = Pattern.compile("\\{([^{}]*)}");

    public CertificateTemplateContent normalize(CertificateTemplateContent content) {
        return new CertificateTemplateContent(
                blankToNull(content.instituteName()),
                content.logoImageId(),
                blankToNull(content.title()),
                blankToNull(content.introText()),
                blankToNull(content.approvalText()),
                blankToNull(content.dictationText()),
                blankToNull(content.qrLabel()),
                blankToNull(content.footerLegend()),
                null,
                null,
                content.signatory1Id(),
                content.signatory2Id());
    }

    public void requireKnownMarkers(CertificateTemplateContent content) {
        requireKnownMarkers(content, ALLOWED_MARKERS);
    }

    public void requireKnownMarkers(CertificateTemplateContent content, Set<String> allowedMarkers) {
        List<String> texts = new ArrayList<>(List.of(
                orEmpty(content.instituteName()), orEmpty(content.title()), orEmpty(content.introText()),
                orEmpty(content.approvalText()), orEmpty(content.dictationText()), orEmpty(content.qrLabel()),
                orEmpty(content.footerLegend())));
        for (CertificateTemplateSigner signer : Arrays.asList(content.firstSigner(), content.secondSigner())) {
            if (signer != null) {
                texts.add(orEmpty(signer.name()));
                texts.add(orEmpty(signer.role()));
            }
        }
        for (String text : texts) {
            Matcher matcher = MARKER.matcher(text);
            while (matcher.find()) {
                if (!allowedMarkers.contains(matcher.group(1))) {
                    throw new TemplateUnknownMarkerException(matcher.group(1));
                }
            }
            String remainder = MARKER.matcher(text).replaceAll("");
            int stray = Math.max(remainder.indexOf('{'), remainder.indexOf('}'));
            if (stray >= 0) {
                throw new TemplateUnknownMarkerException(String.valueOf(remainder.charAt(stray)));
            }
        }
    }

    public void requirePublishable(CertificateTemplateContent content) {
        require(content.title(), "el título");
        if (content.signatory1Id() == null) {
            throw new TemplateIncompleteException("el firmante 1");
        }
        if (content.signatory2Id() == null) {
            throw new TemplateIncompleteException("el firmante 2");
        }
    }

    public void requirePublishableDocument(CertificateTemplateContent content) {
        require(content.title(), "el título");
        boolean anySignature = Stream.of(content.firstSigner(), content.secondSigner())
                .anyMatch(signer -> signer != null && signer.signatureImageId() != null);
        if (!anySignature) {
            throw new TemplateSignatureMissingException();
        }
    }

    private void require(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new TemplateIncompleteException(label);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
