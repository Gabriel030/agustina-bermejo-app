package com.red_escuela.backend.identity.dto;

import com.red_escuela.backend.identity.util.TokenHashUtil;

public record ClientContext(String userAgentHash, String remoteAddress) {

    public static ClientContext of(String userAgent, String remoteAddress) {
        String hash = userAgent == null || userAgent.isBlank() ? null : TokenHashUtil.sha256Hex(userAgent);
        return new ClientContext(hash, remoteAddress);
    }
}
