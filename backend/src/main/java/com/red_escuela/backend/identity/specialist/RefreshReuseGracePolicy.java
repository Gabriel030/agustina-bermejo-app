package com.red_escuela.backend.identity.specialist;

import com.red_escuela.backend.identity.util.TokenHashUtil;
import com.red_escuela.backend.identity.dto.ClientContext;
import com.red_escuela.backend.identity.repository.RefreshTokenState;
import com.red_escuela.backend.shared.domain.StatusType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Component
public class RefreshReuseGracePolicy {

    public static final int MAX_GRACE_SECONDS = 120;

    public enum Verdict {
        GRACE_ALLOWED("grace-allowed"),
        GRACE_DISABLED("grace-disabled"),
        NOT_ROTATED("not-rotated"),
        OUTSIDE_WINDOW("outside-window"),
        USER_AGENT_MISMATCH("ua-mismatch"),
        GRACE_ALREADY_USED("grace-already-used"),
        PERSON_INACTIVE("person-inactive");

        private final String reason;

        Verdict(String reason) {
            this.reason = reason;
        }

        public String reason() {
            return reason;
        }
    }

    private final long graceSeconds;

    public RefreshReuseGracePolicy(@Value("${app.auth.refresh-reuse-grace-seconds}") long graceSeconds) {
        if (graceSeconds < 0 || graceSeconds > MAX_GRACE_SECONDS) {
            throw new IllegalStateException("app.auth.refresh-reuse-grace-seconds debe estar entre 0 y "
                    + MAX_GRACE_SECONDS + " y es " + graceSeconds);
        }
        this.graceSeconds = graceSeconds;
    }

    public Verdict evaluate(RefreshTokenState token, ClientContext clientContext, LocalDateTime now) {
        if (graceSeconds == 0) {
            return Verdict.GRACE_DISABLED;
        }
        if (token.getRotatedAt() == null) {
            return Verdict.NOT_ROTATED;
        }
        if (Duration.between(token.getRotatedAt(), now).compareTo(Duration.ofSeconds(graceSeconds)) > 0) {
            return Verdict.OUTSIDE_WINDOW;
        }
        if (token.getRotatedUserAgentHash() == null || clientContext.userAgentHash() == null
                || !TokenHashUtil.constantTimeEquals(token.getRotatedUserAgentHash(), clientContext.userAgentHash())) {
            return Verdict.USER_AGENT_MISMATCH;
        }
        if (Boolean.TRUE.equals(token.getGraceUsed())) {
            return Verdict.GRACE_ALREADY_USED;
        }
        if (token.getPerson().getStatus() != StatusType.ACTIVE) {
            return Verdict.PERSON_INACTIVE;
        }
        return Verdict.GRACE_ALLOWED;
    }
}
