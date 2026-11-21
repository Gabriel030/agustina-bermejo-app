package com.red_escuela.backend.academicrecord.specialist;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Pattern;

@Component
public class CalendarFeedTokenFactory {

    public static final int TOKEN_LENGTH = 43;
    private static final int TOKEN_BYTES = 32;
    private static final Pattern WELL_FORMED = Pattern.compile("[A-Za-z0-9_-]{" + TOKEN_LENGTH + "}");

    private final SecureRandom secureRandom = new SecureRandom();

    public GeneratedCalendarFeedToken create() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new GeneratedCalendarFeedToken(token, CalendarFeedTokenHasher.hash(token));
    }

    public static boolean isWellFormed(String candidate) {
        return candidate != null && WELL_FORMED.matcher(candidate).matches();
    }

    public record GeneratedCalendarFeedToken(String token, String tokenHash) {
    }
}
