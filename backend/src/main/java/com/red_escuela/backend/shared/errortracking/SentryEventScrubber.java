package com.red_escuela.backend.shared.errortracking;

import io.sentry.Hint;
import io.sentry.SentryEvent;
import io.sentry.SentryOptions;
import io.sentry.protocol.Message;
import io.sentry.protocol.Request;
import io.sentry.protocol.SentryException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

public class SentryEventScrubber implements SentryOptions.BeforeSendCallback {

    static final String MASK = "[redacted]";

    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            "authorization", "cookie", "set-cookie", "proxy-authorization", "x-api-key", "x-auth-token", "x-csrf-token", "x-xsrf-token");

    private static final Pattern JWT = Pattern.compile("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]*");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern LONG_TOKEN = Pattern.compile("(?<![A-Za-z0-9_-])[A-Za-z0-9_-]{32,}(?![A-Za-z0-9_-])");
    private static final Pattern DNI = Pattern.compile("(?<![\\d.])\\d{1,2}\\.\\d{3}\\.\\d{3}(?!\\d)|(?<!\\d)\\d{7,8}(?!\\d)");

    @Override
    public SentryEvent execute(SentryEvent event, Hint hint) {
        scrubRequest(event.getRequest());
        event.setUser(null);
        event.setBreadcrumbs(null);
        event.setServerName(null);
        scrubMessage(event.getMessage());
        List<SentryException> exceptions = event.getExceptions();
        if (exceptions != null) {
            exceptions.forEach(exception -> exception.setValue(maskText(exception.getValue())));
        }
        return event;
    }

    static String maskText(String text) {
        if (text == null) {
            return null;
        }
        String masked = JWT.matcher(text).replaceAll(MASK);
        masked = EMAIL.matcher(masked).replaceAll(MASK);
        masked = LONG_TOKEN.matcher(masked).replaceAll(MASK);
        return DNI.matcher(masked).replaceAll(MASK);
    }

    private static void scrubMessage(Message message) {
        if (message == null) {
            return;
        }
        message.setMessage(maskText(message.getMessage()));
        message.setFormatted(maskText(message.getFormatted()));
        message.setParams(null);
    }

    private static void scrubRequest(Request request) {
        if (request == null) {
            return;
        }
        request.setData(null);
        request.setCookies(null);
        request.setQueryString(null);
        request.setUrl(maskText(withoutQuery(request.getUrl())));
        Map<String, String> headers = request.getHeaders();
        if (headers != null) {
            Map<String, String> kept = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            headers.forEach((name, value) -> {
                if (!SENSITIVE_HEADERS.contains(name.toLowerCase())) {
                    kept.put(name, maskText(value));
                }
            });
            request.setHeaders(kept);
        }
    }

    private static String withoutQuery(String url) {
        if (url == null) {
            return null;
        }
        int queryStart = url.indexOf('?');
        return queryStart < 0 ? url : url.substring(0, queryStart);
    }
}
