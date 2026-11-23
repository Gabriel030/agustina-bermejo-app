package com.red_escuela.backend.shared.errortracking;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SentryTunnelTargetTest {

    @Test
    void buildsEnvelopeUriFromDsnHostAndProjectId() {
        SentryTunnelTarget target = SentryTunnelTarget.fromDsn("https://abc123@errors.example.org/7").orElseThrow();

        assertThat(target.envelopeUri().toString()).isEqualTo("https://errors.example.org/api/7/envelope/");
    }

    @Test
    void keepsPortAndPathPrefix() {
        SentryTunnelTarget target = SentryTunnelTarget.fromDsn("http://key@glitchtip.internal:8000/sentry/12").orElseThrow();

        assertThat(target.envelopeUri().toString()).isEqualTo("http://glitchtip.internal:8000/sentry/api/12/envelope/");
    }

    @Test
    void blankOrMissingDsnIsInert() {
        assertThat(SentryTunnelTarget.fromDsn(null)).isEmpty();
        assertThat(SentryTunnelTarget.fromDsn("")).isEmpty();
        assertThat(SentryTunnelTarget.fromDsn("   ")).isEmpty();
    }

    @Test
    void malformedDsnIsInert() {
        assertThat(SentryTunnelTarget.fromDsn("not a dsn")).isEmpty();
        assertThat(SentryTunnelTarget.fromDsn("ftp://key@host/1")).isEmpty();
        assertThat(SentryTunnelTarget.fromDsn("https://host/1")).isEmpty();
        assertThat(SentryTunnelTarget.fromDsn("https://key@host/abc")).isEmpty();
        assertThat(SentryTunnelTarget.fromDsn("https://key@host")).isEmpty();
    }
}
