package com.red_escuela.backend.billing.annualupfront.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "annual_upfront_terms_version")
public class AnnualUpfrontTermsVersion {
    public static final int MIN_TEXT_LENGTH = 50;
    public static final int MAX_TEXT_LENGTH = 20000;
    private static final String VERSION_PREFIX = "v";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_number", nullable = false, updatable = false)
    private Integer versionNumber;

    @Column(name = "text", nullable = false, updatable = false)
    private String text;

    @Column(name = "published_by_person_id", updatable = false)
    private Long publishedByPersonId;

    @Column(name = "published_at", nullable = false, updatable = false)
    private LocalDateTime publishedAt;

    public static AnnualUpfrontTermsVersion publishVersion(int versionNumber, String text, Long publishedByPersonId, LocalDateTime publishedAt) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(publishedByPersonId, "publishedByPersonId");
        Objects.requireNonNull(publishedAt, "publishedAt");
        if (versionNumber < 1) {
            throw new IllegalArgumentException("The terms version number must be positive");
        }
        if (text.length() < MIN_TEXT_LENGTH || text.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("The terms text must have between " + MIN_TEXT_LENGTH + " and " + MAX_TEXT_LENGTH + " characters");
        }
        AnnualUpfrontTermsVersion version = new AnnualUpfrontTermsVersion();
        version.versionNumber = versionNumber;
        version.text = text;
        version.publishedByPersonId = publishedByPersonId;
        version.publishedAt = publishedAt;
        return version;
    }

    public String versionLabel() {
        return labelOf(versionNumber);
    }

    public AnnualUpfrontTerms toTerms() {
        return new AnnualUpfrontTerms(versionLabel(), text);
    }

    public static String labelOf(int versionNumber) {
        return VERSION_PREFIX + versionNumber;
    }
}
