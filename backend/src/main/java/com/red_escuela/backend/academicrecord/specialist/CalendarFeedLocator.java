package com.red_escuela.backend.academicrecord.specialist;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;

@Component
public class CalendarFeedLocator {

    private static final String FALLBACK_HOST = "calendar.invalid";

    private final String backendBaseUrl;

    public CalendarFeedLocator(@Value("${app.backend.base-url:http://localhost:8080}") String backendBaseUrl) {
        this.backendBaseUrl = backendBaseUrl.endsWith("/")
                ? backendBaseUrl.substring(0, backendBaseUrl.length() - 1)
                : backendBaseUrl;
    }

    public String urlFor(String token) {
        return backendBaseUrl + "/public/calendar/" + token + ".ics";
    }

    public String host() {
        String host = URI.create(backendBaseUrl).getHost();
        return host == null || host.isBlank() ? FALLBACK_HOST : host;
    }
}
