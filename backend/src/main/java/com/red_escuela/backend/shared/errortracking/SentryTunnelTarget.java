package com.red_escuela.backend.shared.errortracking;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

public record SentryTunnelTarget(String dsn, URI envelopeUri) {

    public static Optional<SentryTunnelTarget> fromDsn(String dsn) {
        if (dsn == null || dsn.isBlank()) {
            return Optional.empty();
        }
        try {
            URI parsed = new URI(dsn.trim());
            String scheme = parsed.getScheme();
            boolean httpScheme = "https".equals(scheme) || "http".equals(scheme);
            String host = parsed.getHost();
            String path = parsed.getPath();
            if (!httpScheme || host == null || host.isBlank() || parsed.getUserInfo() == null
                    || path == null || path.length() < 2) {
                return Optional.empty();
            }
            int lastSlash = path.lastIndexOf('/');
            String projectId = path.substring(lastSlash + 1);
            if (projectId.isBlank() || !projectId.chars().allMatch(Character::isDigit)) {
                return Optional.empty();
            }
            String prefix = path.substring(0, lastSlash);
            URI envelopeUri = new URI(scheme, null, host, parsed.getPort(),
                    prefix + "/api/" + projectId + "/envelope/", null, null);
            return Optional.of(new SentryTunnelTarget(dsn.trim(), envelopeUri));
        } catch (URISyntaxException invalidDsn) {
            return Optional.empty();
        }
    }
}
