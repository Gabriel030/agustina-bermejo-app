package com.red_escuela.backend.testsupport;

import org.springframework.core.Ordered;
import org.springframework.test.context.BootstrapUtils;
import org.springframework.test.context.MergedContextConfiguration;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestContextBootstrapper;
import org.springframework.test.context.TestExecutionListener;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SpringContextCensusListener implements TestExecutionListener, Ordered {

    private static final Path OUTPUT = Path.of("target", "spring-contexts", "contexts.txt");
    private static final Pattern IDENTITY = Pattern.compile("@[0-9a-f]{4,}");
    private static final Set<String> RECORDED = ConcurrentHashMap.newKeySet();

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public void prepareTestInstance(TestContext testContext) {
        Class<?> testClass = testContext.getTestClass();
        if (testClass.getEnclosingClass() != null) {
            return;
        }
        try {
            TestContextBootstrapper bootstrapper = BootstrapUtils.resolveTestContextBootstrapper(testClass);
            String fingerprint = fingerprint(bootstrapper.buildMergedContextConfiguration());
            if (RECORDED.add(fingerprint)) {
                append(fingerprint + "\t" + testClass.getName() + System.lineSeparator());
            }
        } catch (RuntimeException exception) {
            return;
        }
    }

    static String fingerprint(MergedContextConfiguration config) {
        List<String> parts = new ArrayList<>();
        parts.add("classes=" + names(config.getClasses()));
        parts.add("locations=" + sorted(List.of(config.getLocations())));
        parts.add("profiles=" + sorted(List.of(config.getActiveProfiles())));
        parts.add("properties=" + sorted(List.of(config.getPropertySourceProperties())));
        parts.add("initializers=" + names(config.getContextInitializerClasses().toArray(new Class<?>[0])));
        parts.add("customizers=" + sorted(config.getContextCustomizers().stream()
                .map(customizer -> customizer.getClass().getName() + ":" + describe(customizer, 0))
                .collect(Collectors.toList())));
        parts.add("loader=" + config.getContextLoader().getClass().getName());
        parts.add("parent=" + (config.getParent() == null ? "" : fingerprint(config.getParent())));
        return sha256(String.join("\n", parts));
    }

    private static String describe(Object value, int depth) {
        if (value == null) {
            return "null";
        }
        if (value instanceof CharSequence || value instanceof Number || value instanceof Boolean || value instanceof Enum<?>) {
            return value.toString();
        }
        if (value instanceof Class<?> type) {
            return type.getName();
        }
        if (value instanceof Field) {
            return "";
        }
        if (value instanceof Method method) {
            return method.toString();
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(item -> describe(item, depth + 1)).sorted().collect(Collectors.joining(",", "[", "]"));
        }
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .map(entry -> describe(entry.getKey(), depth + 1) + "=" + describe(entry.getValue(), depth + 1))
                    .sorted().collect(Collectors.joining(",", "{", "}"));
        }
        if (value.getClass().isArray() && value instanceof Object[] array) {
            return describe(List.of(array), depth + 1);
        }
        String text = value.toString();
        if (!IDENTITY.matcher(text).find()) {
            return text;
        }
        Class<?> type = value.getClass();
        if (depth > 5 || type.getName().startsWith("java.")) {
            return type.getName();
        }
        List<String> fields = new ArrayList<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getName().equals("testClassNames")) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    fields.add(field.getName() + "=" + describe(field.get(value), depth + 1));
                } catch (ReflectiveOperationException | RuntimeException exception) {
                    fields.add(field.getName() + "=?");
                }
            }
        }
        return type.getName() + fields.stream().sorted().collect(Collectors.joining(",", "{", "}"));
    }

    private static String names(Class<?>[] classes) {
        List<String> names = new ArrayList<>();
        for (Class<?> type : classes) {
            names.add(type.getName());
        }
        return sorted(names);
    }

    private static String sorted(List<String> values) {
        return values.stream().sorted().collect(Collectors.joining("|"));
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                hex.append(String.format("%02x", digest[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static synchronized void append(String line) {
        try {
            Files.createDirectories(OUTPUT.getParent());
            Files.writeString(OUTPUT, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException exception) {
            return;
        }
    }
}
