package com.red_escuela.backend.billing.annualupfront.domain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-236")
class AnnualUpfrontTermsVersionTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2027, 2, 1, 9, 0);

    @Test
    void aPublishedVersionKeepsItsNumberTextAuthorAndMomentAndLabelsItWithAPrefix() {
        AnnualUpfrontTermsVersion version = AnnualUpfrontTermsVersion.publishVersion(3, "x".repeat(50), 7L, NOW);

        assertThat(version.getVersionNumber()).isEqualTo(3);
        assertThat(version.versionLabel()).isEqualTo("v3");
        assertThat(version.getPublishedByPersonId()).isEqualTo(7L);
        assertThat(version.getPublishedAt()).isEqualTo(NOW);
        assertThat(version.toTerms()).isEqualTo(new AnnualUpfrontTerms("v3", "x".repeat(50)));
    }

    @Test
    void theTextBoundsAreInclusive() {
        assertThat(AnnualUpfrontTermsVersion.publishVersion(1, "x".repeat(50), 1L, NOW)).isNotNull();
        assertThat(AnnualUpfrontTermsVersion.publishVersion(1, "x".repeat(20000), 1L, NOW)).isNotNull();
        assertThatThrownBy(() -> AnnualUpfrontTermsVersion.publishVersion(1, "x".repeat(49), 1L, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AnnualUpfrontTermsVersion.publishVersion(1, "x".repeat(20001), 1L, NOW)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aVersionNumberBelowOneIsRejected() {
        assertThatThrownBy(() -> AnnualUpfrontTermsVersion.publishVersion(0, "x".repeat(50), 1L, NOW)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theAuthorAndTheMomentAreRequired() {
        assertThatThrownBy(() -> AnnualUpfrontTermsVersion.publishVersion(1, "x".repeat(50), null, NOW)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AnnualUpfrontTermsVersion.publishVersion(1, "x".repeat(50), 1L, null)).isInstanceOf(NullPointerException.class);
    }
}
