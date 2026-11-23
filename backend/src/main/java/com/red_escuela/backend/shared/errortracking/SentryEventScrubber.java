package com.red_escuela.backend.shared.errortracking;

import com.red_escuela.backend.shared.exception.ErrorCodeResponse;
import io.sentry.Hint;
import io.sentry.SentryEvent;
import io.sentry.SentryOptions;
import io.sentry.protocol.SentryException;
import io.sentry.protocol.SentryStackFrame;
import io.sentry.protocol.SentryStackTrace;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;

public class SentryEventScrubber implements SentryOptions.BeforeSendCallback {

    static final String MASK = "[redacted]";
    static final String REQUEST_ID_TAG = "requestId";
    static final int MAX_MASKED_LENGTH = 2000;

    private static final Pattern UUID_FORMAT = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern JWT = Pattern.compile("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]*");
    private static final Pattern EMAIL = Pattern.compile("[\\p{L}\\p{N}._%+-]+@[\\p{L}\\p{N}.-]+\\.\\p{L}{2,}");
    private static final Pattern CUIL = Pattern.compile("(?<!\\d)\\d{2}-?\\d{7,8}-?\\d(?!\\d)");
    private static final Pattern PHONE = Pattern.compile("\\+?\\d[\\d\\s().-]{8,}\\d");
    private static final Pattern LONG_TOKEN = Pattern.compile("(?<![A-Za-z0-9_-])[A-Za-z0-9_-]{32,}(?![A-Za-z0-9_-])");
    private static final Pattern DNI = Pattern.compile("(?<![\\d.])\\d{1,2}[.\\s,]\\d{3}[.\\s,]\\d{3}(?!\\d)|(?<!\\d)\\d{7,8}(?!\\d)");

    @Override
    public SentryEvent execute(SentryEvent event, Hint hint) {
        Map<String, String> tags = event.getTags();
        String requestId = tags == null ? null : tags.get(REQUEST_ID_TAG);
        event.setTags(requestId != null && UUID_FORMAT.matcher(requestId).matches() ? Map.of(REQUEST_ID_TAG, requestId) : Map.of());
        event.setRequest(null);
        event.setUser(null);
        event.setBreadcrumbs(null);
        event.setServerName(null);
        event.setMessage(null);
        event.setExtras(null);
        event.getContexts().clear();
        event.setThreads(null);
        event.setFingerprints(null);
        event.setTransaction(null);
        event.setDebugMeta(null);
        event.setModules(null);
        event.setLogger(null);
        event.setDist(null);
        event.setUnknown(null);
        scrubExceptions(event);
        return event;
    }

    static String maskText(String text) {
        if (text == null) {
            return null;
        }
        String masked = text.length() > MAX_MASKED_LENGTH ? text.substring(0, MAX_MASKED_LENGTH) : text;
        masked = JWT.matcher(masked).replaceAll(MASK);
        masked = EMAIL.matcher(masked).replaceAll(MASK);
        masked = CUIL.matcher(masked).replaceAll(MASK);
        masked = PHONE.matcher(masked).replaceAll(MASK);
        masked = LONG_TOKEN.matcher(masked).replaceAll(MASK);
        return DNI.matcher(masked).replaceAll(MASK);
    }

    private static void scrubExceptions(SentryEvent event) {
        List<SentryException> exceptions = event.getExceptions();
        if (exceptions == null) {
            return;
        }
        List<Throwable> rootFirst = causeChain(event.getThrowable());
        Collections.reverse(rootFirst);
        for (int index = 0; index < exceptions.size(); index++) {
            SentryException exception = exceptions.get(index);
            exception.setValue(index < rootFirst.size() ? safeValue(rootFirst.get(index)) : null);
            exception.setMechanism(null);
            scrubStackTrace(exception.getStacktrace());
        }
    }

    private static List<Throwable> causeChain(Throwable top) {
        List<Throwable> chain = new ArrayList<>();
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable current = top; current != null && seen.add(current); current = current.getCause()) {
            chain.add(current);
        }
        return chain;
    }

    private static String safeValue(Throwable throwable) {
        StringBuilder value = new StringBuilder("code=").append(ErrorCodeResponse.codeOf(throwable.getClass()));
        if (throwable instanceof SQLException sqlException && sqlException.getSQLState() != null) {
            value.append(" sqlState=").append(sqlException.getSQLState());
        }
        if (throwable instanceof PSQLException postgresException) {
            ServerErrorMessage serverError = postgresException.getServerErrorMessage();
            if (serverError != null && serverError.getConstraint() != null) {
                value.append(" constraint=").append(serverError.getConstraint());
            }
        }
        return maskText(value.toString());
    }

    private static void scrubStackTrace(SentryStackTrace stackTrace) {
        if (stackTrace == null || stackTrace.getFrames() == null) {
            return;
        }
        for (SentryStackFrame frame : stackTrace.getFrames()) {
            frame.setVars(null);
            frame.setContextLine(null);
            frame.setPreContext(null);
            frame.setPostContext(null);
            frame.setAbsPath(null);
        }
        stackTrace.setRegisters(null);
    }
}
