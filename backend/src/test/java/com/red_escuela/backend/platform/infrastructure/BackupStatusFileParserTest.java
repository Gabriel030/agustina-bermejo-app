package com.red_escuela.backend.platform.infrastructure;

import com.red_escuela.backend.platform.domain.BackupAlertCode;
import com.red_escuela.backend.platform.domain.BackupStatusSnapshot;
import com.red_escuela.backend.platform.domain.BackupTier;
import com.red_escuela.backend.platform.domain.BackupVerification;
import com.red_escuela.backend.platform.domain.RestoreTestFailureCode;
import com.red_escuela.backend.platform.domain.RestoreTestResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BackupStatusFileParserTest {

    static String validJson() {
        return """
{
  "schemaVersion": 1,
  "generatedAt": "2026-11-20T10:00:00Z",
  "targetRpoHours": 4,
  "destination": {"path": "/var/backups/agustina", "freeBytes": 100, "totalBytes": 500},
  "tiers": [
    {"tier": "DAILY", "takenAt": "2026-11-20T03:00:00Z", "label": null, "dumpSizeBytes": 10, "filesSizeBytes": 20,
     "verification": "VERIFIED", "kept": 7, "fileNames": ["daily-2026-11-20.dump.age", "daily-2026-11-20-files.tar.age"]},
    {"tier": "PRE_RELEASE", "takenAt": "2026-11-18T21:30:00+00:00", "label": "v1.42.0-rc.2", "dumpSizeBytes": 10, "filesSizeBytes": 20,
     "verification": "UNVERIFIED", "kept": 3, "fileNames": []}
  ],
  "lastRestoreTest": {"ranAt": "2026-11-19T04:00:00Z", "result": "PASSED", "failureCode": null},
  "alerts": [{"at": "2026-11-19T04:00:00Z", "level": "WARNING", "code": "DISK_LOW"}],
  "consecutiveFailures": 0,
  "lastCompleteBackupAt": "2026-11-20T08:00:00Z"
}
""";
    }

    private static BackupStatusSnapshot parse(String json) throws IOException {
        return BackupStatusFileParser.parse(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }

    private static void assertRejected(String json) {
        assertThatThrownBy(() -> parse(json)).isInstanceOfAny(InvalidBackupStatusFileException.class, IOException.class);
    }

    @Test
    void validFileIsParsedWithItsContent() throws IOException {
        BackupStatusSnapshot snapshot = parse(validJson());

        assertThat(snapshot.generatedAt()).isEqualTo(Instant.parse("2026-11-20T10:00:00Z"));
        assertThat(snapshot.lastCompleteBackupAt()).isEqualTo(Instant.parse("2026-11-20T08:00:00Z"));
        assertThat(snapshot.disk().path()).isEqualTo("/var/backups/agustina");
        assertThat(snapshot.tiers()).hasSize(2);
        assertThat(snapshot.tiers().get(0).tier()).isEqualTo(BackupTier.DAILY);
        assertThat(snapshot.tiers().get(0).fileNames()).hasSize(2);
        assertThat(snapshot.tiers().get(1).label()).isEqualTo("v1.42.0-rc.2");
        assertThat(snapshot.tiers().get(1).verification()).isEqualTo(BackupVerification.UNVERIFIED);
        assertThat(snapshot.tiers().get(1).takenAt()).isEqualTo(Instant.parse("2026-11-18T21:30:00Z"));
        assertThat(snapshot.lastRestoreTest().result()).isEqualTo(RestoreTestResult.PASSED);
        assertThat(snapshot.alerts().get(0).code()).isEqualTo(BackupAlertCode.DISK_LOW);
        assertThat(snapshot.consecutiveFailures()).isZero();
    }

    @Test
    void nullRestoreTestAndNullLastCompleteBackupAreAccepted() throws IOException {
        String json = validJson().replaceAll("\"lastRestoreTest\": \\{[^}]*}", "\"lastRestoreTest\": null")
                .replace("\"lastCompleteBackupAt\": \"2026-11-20T08:00:00Z\"", "\"lastCompleteBackupAt\": null");

        BackupStatusSnapshot snapshot = parse(json);

        assertThat(snapshot.lastRestoreTest()).isNull();
        assertThat(snapshot.lastCompleteBackupAt()).isNull();
    }

    @Test
    void failedRestoreTestWithItsCodeIsAccepted() throws IOException {
        String json = validJson().replace("\"result\": \"PASSED\", \"failureCode\": null", "\"result\": \"FAILED\", \"failureCode\": \"CHECKSUM_MISMATCH\"");

        assertThat(parse(json).lastRestoreTest().failureCode()).isEqualTo(RestoreTestFailureCode.CHECKSUM_MISMATCH);
    }

    @ParameterizedTest
    @ValueSource(strings = {"v1.42.0-beta", "v1.42.0-rc1", "v1.42.0-rc.2", "1.42.0"})
    void preReleaseLabelWithAVersionSuffixIsKept(String label) throws IOException {
        BackupStatusSnapshot snapshot = parse(validJson().replace("v1.42.0-rc.2", label));

        assertThat(snapshot.tiers().get(1).label()).isEqualTo(label);
    }

    @ParameterizedTest
    @ValueSource(strings = {"<b>v1.0.0</b>", "v1.42.0 -rc.1", "v1.42.0-rc.1111111111111111111111111111111111"})
    void unexpectedPreReleaseLabelIsNormalizedToNullAndTheFileIsStillRead(String label) throws IOException {
        BackupStatusSnapshot snapshot = parse(validJson().replace("v1.42.0-rc.2", label));

        assertThat(snapshot.tiers().get(1).label()).isNull();
        assertThat(snapshot.tiers()).hasSize(2);
    }

    @Test
    void labelOnATierThatIsNotPreReleaseIsNormalizedToNull() throws IOException {
        BackupStatusSnapshot snapshot = parse(validJson().replace("\"label\": null, \"dumpSizeBytes\": 10", "\"label\": \"v1.0.0\", \"dumpSizeBytes\": 10"));

        assertThat(snapshot.tiers().get(0).label()).isNull();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"..", ".", "a b", "a/b", "a;b", "ñ.dump"})
    void invalidFileNameIsRejected(String fileName) {
        assertRejected(validJson().replace("daily-2026-11-20.dump.age", fileName));
    }

    @ParameterizedTest
    @ValueSource(strings = {"...", "", "a/b", "../a", "a\\\\b"})
    void dotOnlyEmptyOrSeparatorFileNamesAreRejected(String fileName) {
        assertRejected(validJson().replace("daily-2026-11-20.dump.age", fileName));
    }

    @ParameterizedTest
    @ValueSource(strings = {".a", "a.", "a..b", "a_b-c.dump.age", "-", "_"})
    void fileNamesWithDotsInsideOtherCharactersAreAccepted(String fileName) throws IOException {
        BackupStatusSnapshot snapshot = parse(validJson().replace("daily-2026-11-20.dump.age", fileName));

        assertThat(snapshot.tiers().get(0).fileNames()).contains(fileName);
    }

    @Test
    void fileNameLongerThan128IsRejected() {
        assertRejected(validJson().replace("daily-2026-11-20.dump.age", "a".repeat(129)));
    }

    @Test
    void moreThan100FileNamesAreRejected() {
        String names = String.join(",", java.util.Collections.nCopies(101, "\"a.dump\""));
        assertRejected(validJson().replace("\"fileNames\": []", "\"fileNames\": [" + names + "]"));
    }

    @Test
    void moreThanTwentyTiersAreRejected() {
        String tier = "{\"tier\": \"INTRADAY\", \"takenAt\": \"2026-11-20T03:00:00Z\", \"label\": null, \"dumpSizeBytes\": 1, "
                + "\"filesSizeBytes\": 1, \"verification\": \"VERIFIED\", \"kept\": 1, \"fileNames\": []}";
        String tiers = String.join(",", java.util.Collections.nCopies(21, tier));
        assertRejected(validJson().replaceAll("(?s)\"tiers\": \\[.*?\\],\\n  \"lastRestoreTest\"", "\"tiers\": [" + tiers + "],\n  \"lastRestoreTest\""));
    }

    @Test
    void moreThan200AlertsAreRejected() {
        String alert = "{\"at\": \"2026-11-19T04:00:00Z\", \"level\": \"WARNING\", \"code\": \"DISK_LOW\"}";
        String alerts = String.join(",", java.util.Collections.nCopies(201, alert));
        assertRejected(validJson().replaceAll("\"alerts\": \\[[^\\]]*]", "\"alerts\": [" + alerts + "]"));
    }

    @Test
    void unsupportedSchemaVersionIsRejected() {
        assertRejected(validJson().replace("\"schemaVersion\": 1", "\"schemaVersion\": 2"));
    }

    @Test
    void unknownEnumValuesAreRejected() {
        assertRejected(validJson().replace("\"tier\": \"DAILY\"", "\"tier\": \"MONTHLY\""));
        assertRejected(validJson().replace("\"verification\": \"VERIFIED\"", "\"verification\": \"OK\""));
        assertRejected(validJson().replace("\"code\": \"DISK_LOW\"", "\"code\": \"Sin espacio en disco\""));
        assertRejected(validJson().replace("\"level\": \"WARNING\"", "\"level\": \"INFO\""));
    }

    @Test
    void restoreTestFailureCodeMustMatchTheResult() {
        assertRejected(validJson().replace("\"result\": \"PASSED\", \"failureCode\": null", "\"result\": \"PASSED\", \"failureCode\": \"RESTORE_FAILED\""));
        assertRejected(validJson().replace("\"result\": \"PASSED\", \"failureCode\": null", "\"result\": \"FAILED\", \"failureCode\": null"));
        assertRejected(validJson().replace("\"result\": \"PASSED\", \"failureCode\": null", "\"result\": \"FAILED\", \"failureCode\": \"salida libre del comando\""));
    }

    @Test
    void unknownAndMissingFieldsAreRejected() {
        assertRejected(validJson().replace("\"consecutiveFailures\": 0,", "\"consecutiveFailures\": 0, \"extra\": 1,"));
        assertRejected(validJson().replace("  \"consecutiveFailures\": 0,\n", ""));
        assertRejected(validJson().replace("\"kept\": 7,", "\"kept\": 7, \"note\": \"texto libre\","));
        assertRejected(validJson().replace("\"freeBytes\": 100, ", ""));
    }

    @Test
    void wrongTypesAndNegativeNumbersAreRejected() {
        assertRejected(validJson().replace("\"freeBytes\": 100", "\"freeBytes\": -1"));
        assertRejected(validJson().replace("\"freeBytes\": 100", "\"freeBytes\": 1.5"));
        assertRejected(validJson().replace("\"freeBytes\": 100", "\"freeBytes\": \"100\""));
        assertRejected(validJson().replace("\"targetRpoHours\": 4", "\"targetRpoHours\": 0"));
        assertRejected(validJson().replace("\"consecutiveFailures\": 0", "\"consecutiveFailures\": -1"));
        assertRejected(validJson().replace("\"generatedAt\": \"2026-11-20T10:00:00Z\"", "\"generatedAt\": \"ayer\""));
    }

    @Test
    void diskPathLongerThan256IsRejected() {
        assertRejected(validJson().replace("/var/backups/agustina", "/" + "a".repeat(256)));
    }

    @Test
    void duplicateKeysAndTrailingContentAreRejected() {
        assertRejected(validJson().replace("\"schemaVersion\": 1,", "\"schemaVersion\": 1, \"schemaVersion\": 1,"));
        assertRejected(validJson() + " {}");
    }

    @Test
    void rootThatIsNotAnObjectIsRejected() {
        assertRejected("[]");
        assertRejected("null");
        assertRejected("");
    }
}
