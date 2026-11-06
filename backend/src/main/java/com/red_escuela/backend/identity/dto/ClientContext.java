package com.red_escuela.backend.identity.dto;

import com.red_escuela.backend.identity.util.TokenHashUtil;

public record ClientContext(String userAgentHash, String remoteAddress) {

    public static ClientContext of(String userAgent, String remoteAddress) {
        return new ClientContext(TokenHashUtil.sha256Hex(userAgent == null ? "" : userAgent), remoteAddress);
    }
}
