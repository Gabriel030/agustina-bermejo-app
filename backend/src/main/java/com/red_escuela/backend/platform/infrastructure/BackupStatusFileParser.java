package com.red_escuela.backend.platform.infrastructure;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.red_escuela.backend.platform.domain.BackupAlert;
import com.red_escuela.backend.platform.domain.BackupAlertCode;
import com.red_escuela.backend.platform.domain.BackupAlertLevel;
import com.red_escuela.backend.platform.domain.BackupDisk;
import com.red_escuela.backend.platform.domain.BackupStatusSnapshot;
import com.red_escuela.backend.platform.domain.BackupTier;
import com.red_escuela.backend.platform.domain.BackupTierStatus;
import com.red_escuela.backend.platform.domain.BackupVerification;
import com.red_escuela.backend.platform.domain.RestoreTestFailureCode;
import com.red_escuela.backend.platform.domain.RestoreTestResult;
import com.red_escuela.backend.platform.domain.RestoreTestStatus;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

final class BackupStatusFileParser {

    private static final int SCHEMA_VERSION = 1;
    private static final int MAX_TIERS = 20;
    private static final int MAX_FILE_NAMES = 100;
    private static final int MAX_ALERTS = 200;
    private static final int MAX_PATH_LENGTH = 256;
    private static final int MAX_LABEL_LENGTH = 32;
    private static final int MAX_FILE_NAME_LENGTH = 128;
    private static final Pattern LABEL = Pattern.compile("^v?[0-9]+\\.[0-9]+\\.[0-9]+(-rc\\.[0-9]+)?$");
    private static final Pattern FILE_NAME = Pattern.compile("^(?!\\.+$)[A-Za-z0-9._-]+$");
    private static final Set<String> ROOT_FIELDS = Set.of("schemaVersion", "generatedAt", "targetRpoHours", "destination", "tiers",
            "lastRestoreTest", "alerts", "consecutiveFailures", "lastCompleteBackupAt");
    private static final Set<String> DESTINATION_FIELDS = Set.of("path", "freeBytes", "totalBytes");
    private static final Set<String> TIER_FIELDS = Set.of("tier", "takenAt", "label", "dumpSizeBytes", "filesSizeBytes",
            "verification", "kept", "fileNames");
    private static final Set<String> RESTORE_TEST_FIELDS = Set.of("ranAt", "result", "failureCode");
    private static final Set<String> ALERT_FIELDS = Set.of("at", "level", "code");

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    private BackupStatusFileParser() {
    }

    static BackupStatusSnapshot parse(InputStream content) throws IOException {
        JsonNode root = MAPPER.readTree(content);
        requireObject(root, "root", ROOT_FIELDS);
        requireSchemaVersion(root.get("schemaVersion"));
        requireInteger(root.get("targetRpoHours"), "targetRpoHours", 1, Integer.MAX_VALUE);
        return new BackupStatusSnapshot(
                instant(root.get("generatedAt"), "generatedAt"),
                nullableInstant(root.get("lastCompleteBackupAt"), "lastCompleteBackupAt"),
                disk(root.get("destination")),
                tiers(root.get("tiers")),
                restoreTest(root.get("lastRestoreTest")),
                alerts(root.get("alerts")),
                (int) requireInteger(root.get("consecutiveFailures"), "consecutiveFailures", 0, Integer.MAX_VALUE));
    }

    private static void requireSchemaVersion(JsonNode node) {
        if (requireInteger(node, "schemaVersion", SCHEMA_VERSION, SCHEMA_VERSION) != SCHEMA_VERSION) {
            throw invalid("schemaVersion");
        }
    }

    private static BackupDisk disk(JsonNode node) {
        requireObject(node, "destination", DESTINATION_FIELDS);
        return new BackupDisk(requireInteger(node.get("freeBytes"), "destination.freeBytes", 0, Long.MAX_VALUE),
                requireInteger(node.get("totalBytes"), "destination.totalBytes", 0, Long.MAX_VALUE),
                text(node.get("path"), "destination.path", MAX_PATH_LENGTH));
    }

    private static List<BackupTierStatus> tiers(JsonNode node) {
        requireArray(node, "tiers", MAX_TIERS);
        List<BackupTierStatus> tiers = new ArrayList<>();
        for (JsonNode tierNode : node) {
            tiers.add(tier(tierNode));
        }
        return List.copyOf(tiers);
    }

    private static BackupTierStatus tier(JsonNode node) {
        requireObject(node, "tiers[]", TIER_FIELDS);
        BackupTier tier = enumValue(BackupTier.class, node.get("tier"), "tiers[].tier");
        return new BackupTierStatus(tier, instant(node.get("takenAt"), "tiers[].takenAt"), label(node.get("label"), tier),
                requireInteger(node.get("dumpSizeBytes"), "tiers[].dumpSizeBytes", 0, Long.MAX_VALUE),
                requireInteger(node.get("filesSizeBytes"), "tiers[].filesSizeBytes", 0, Long.MAX_VALUE),
                enumValue(BackupVerification.class, node.get("verification"), "tiers[].verification"),
                (int) requireInteger(node.get("kept"), "tiers[].kept", 0, Integer.MAX_VALUE),
                fileNames(node.get("fileNames")));
    }

    private static String label(JsonNode node, BackupTier tier) {
        if (node == null || !node.isNull() && !node.isTextual()) {
            throw invalid("tiers[].label");
        }
        if (node.isNull()) {
            return null;
        }
        String label = node.asText();
        if (tier != BackupTier.PRE_RELEASE || label.length() > MAX_LABEL_LENGTH || !LABEL.matcher(label).matches()) {
            throw invalid("tiers[].label");
        }
        return label;
    }

    private static List<String> fileNames(JsonNode node) {
        requireArray(node, "tiers[].fileNames", MAX_FILE_NAMES);
        List<String> fileNames = new ArrayList<>();
        for (JsonNode fileName : node) {
            String value = text(fileName, "tiers[].fileNames[]", MAX_FILE_NAME_LENGTH);
            if (!FILE_NAME.matcher(value).matches()) {
                throw invalid("tiers[].fileNames[]");
            }
            fileNames.add(value);
        }
        return List.copyOf(fileNames);
    }

    private static RestoreTestStatus restoreTest(JsonNode node) {
        if (node == null) {
            throw invalid("lastRestoreTest");
        }
        if (node.isNull()) {
            return null;
        }
        requireObject(node, "lastRestoreTest", RESTORE_TEST_FIELDS);
        RestoreTestResult result = enumValue(RestoreTestResult.class, node.get("result"), "lastRestoreTest.result");
        JsonNode failureCodeNode = node.get("failureCode");
        RestoreTestFailureCode failureCode = failureCodeNode.isNull() ? null
                : enumValue(RestoreTestFailureCode.class, failureCodeNode, "lastRestoreTest.failureCode");
        if ((result == RestoreTestResult.PASSED) != (failureCode == null)) {
            throw invalid("lastRestoreTest.failureCode");
        }
        return new RestoreTestStatus(instant(node.get("ranAt"), "lastRestoreTest.ranAt"), result, failureCode);
    }

    private static List<BackupAlert> alerts(JsonNode node) {
        requireArray(node, "alerts", MAX_ALERTS);
        List<BackupAlert> alerts = new ArrayList<>();
        for (JsonNode alertNode : node) {
            requireObject(alertNode, "alerts[]", ALERT_FIELDS);
            alerts.add(new BackupAlert(instant(alertNode.get("at"), "alerts[].at"),
                    enumValue(BackupAlertLevel.class, alertNode.get("level"), "alerts[].level"),
                    enumValue(BackupAlertCode.class, alertNode.get("code"), "alerts[].code")));
        }
        return List.copyOf(alerts);
    }

    private static void requireObject(JsonNode node, String name, Set<String> fields) {
        if (node == null || !node.isObject() || node.size() != fields.size()) {
            throw invalid(name);
        }
        node.fieldNames().forEachRemaining(field -> {
            if (!fields.contains(field)) {
                throw invalid(name + "." + field);
            }
        });
    }

    private static void requireArray(JsonNode node, String name, int maxItems) {
        if (node == null || !node.isArray() || node.size() > maxItems) {
            throw invalid(name);
        }
    }

    private static long requireInteger(JsonNode node, String name, long min, long max) {
        if (node == null || !node.isIntegralNumber() || !node.canConvertToLong()) {
            throw invalid(name);
        }
        long value = node.asLong();
        if (value < min || value > max) {
            throw invalid(name);
        }
        return value;
    }

    private static String text(JsonNode node, String name, int maxLength) {
        if (node == null || !node.isTextual() || node.asText().length() > maxLength) {
            throw invalid(name);
        }
        return node.asText();
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, JsonNode node, String name) {
        if (node == null || !node.isTextual()) {
            throw invalid(name);
        }
        try {
            return Enum.valueOf(type, node.asText());
        } catch (IllegalArgumentException e) {
            throw invalid(name);
        }
    }

    private static Instant nullableInstant(JsonNode node, String name) {
        if (node != null && node.isNull()) {
            return null;
        }
        return instant(node, name);
    }

    private static Instant instant(JsonNode node, String name) {
        if (node == null || !node.isTextual()) {
            throw invalid(name);
        }
        try {
            return OffsetDateTime.parse(node.asText()).toInstant();
        } catch (DateTimeParseException e) {
            throw invalid(name);
        }
    }

    private static InvalidBackupStatusFileException invalid(String field) {
        return new InvalidBackupStatusFileException(field);
    }
}
