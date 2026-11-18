package com.red_escuela.backend.shared.audit;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AuditValueMasker {

    public static final String MASK = "***";

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    private static final Set<String> SENSITIVE_TOKENS = Set.of("password", "passwd", "pwd", "token", "secret",
            "authorization", "cbu", "cuit", "dni", "cvv", "otp", "pin", "card", "hash", "credential", "credentials",
            "apikey", "privatekey", "cardnumber");

    private static final Set<String> CODE_QUALIFIERS = Set.of("verification", "reset", "confirmation", "security",
            "sms", "auth", "access", "recovery", "otp", "twofactor", "2fa");

    private static final Pattern KEY_BEFORE_SEPARATOR = Pattern.compile("([A-Za-z][A-Za-z0-9_\\-]*)\\s*[:=]");
    private static final Pattern CAMEL_BOUNDARY = Pattern.compile("(?<=[a-z0-9])(?=[A-Z])");
    private static final Pattern KEY_SPLIT = Pattern.compile("[^A-Za-z0-9]+");

    private AuditValueMasker() {
    }

    public static String mask(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        if (looksLikeJson(value)) {
            return maskJsonOrClose(value);
        }
        return containsSensitiveKey(value) ? MASK : value;
    }

    private static String maskJsonOrClose(String value) {
        try {
            JsonNode root = MAPPER.readTree(value);
            return MAPPER.writeValueAsString(maskNode(root));
        } catch (RuntimeException e) {
            return MASK;
        }
    }

    private static JsonNode maskNode(JsonNode node) {
        if (node.isObject()) {
            ObjectNode masked = MAPPER.createObjectNode();
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                JsonNode child = field.getValue();
                if (isSensitiveKey(field.getKey()) && !child.isNull()) {
                    masked.put(field.getKey(), MASK);
                } else {
                    masked.set(field.getKey(), maskNode(child));
                }
            }
            return masked;
        }
        if (node.isArray()) {
            ArrayNode masked = MAPPER.createArrayNode();
            node.forEach(element -> masked.add(maskNode(element)));
            return masked;
        }
        if (node.isString()) {
            return MAPPER.getNodeFactory().stringNode(mask(node.asString()));
        }
        return node;
    }

    private static boolean looksLikeJson(String value) {
        String trimmed = value.stripLeading();
        return trimmed.startsWith("{") || trimmed.startsWith("[");
    }

    private static boolean containsSensitiveKey(String text) {
        Matcher matcher = KEY_BEFORE_SEPARATOR.matcher(text);
        while (matcher.find()) {
            if (isSensitiveKey(matcher.group(1))) {
                return true;
            }
        }
        return false;
    }

    static boolean isSensitiveKey(String key) {
        List<String> tokens = new ArrayList<>();
        for (String part : KEY_SPLIT.split(key)) {
            for (String token : CAMEL_BOUNDARY.split(part)) {
                if (!token.isEmpty()) {
                    tokens.add(token.toLowerCase(Locale.ROOT));
                }
            }
        }
        String joined = String.join("", tokens);
        if (SENSITIVE_TOKENS.contains(joined) || tokens.stream().anyMatch(SENSITIVE_TOKENS::contains)) {
            return true;
        }
        for (int i = 0; i + 1 < tokens.size(); i++) {
            if (SENSITIVE_TOKENS.contains(tokens.get(i) + tokens.get(i + 1))) {
                return true;
            }
        }
        if (tokens.contains("code")) {
            return tokens.size() == 1 || tokens.stream().anyMatch(CODE_QUALIFIERS::contains);
        }
        return false;
    }
}
