package com.red_escuela.backend.identity.dto;

import com.red_escuela.backend.identity.util.TokenHashUtil;

public record LegalAcceptanceEvidence(String ipHash, String userAgentSummary) {

    private static final int USER_AGENT_MAX_LENGTH = 120;

    public static LegalAcceptanceEvidence of(String remoteAddress, String userAgent) {
        String ipHash = remoteAddress == null || remoteAddress.isBlank() ? null : TokenHashUtil.sha256Hex(remoteAddress.trim());
        String summary = userAgent == null || userAgent.isBlank() ? null : truncated(userAgent.trim());
        return new LegalAcceptanceEvidence(ipHash, summary);
    }

    public static LegalAcceptanceEvidence none() {
        return new LegalAcceptanceEvidence(null, null);
    }

    private static String truncated(String value) {
        return value.length() <= USER_AGENT_MAX_LENGTH ? value : value.substring(0, USER_AGENT_MAX_LENGTH);
    }
}
