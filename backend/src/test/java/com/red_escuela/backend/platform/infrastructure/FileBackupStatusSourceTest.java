package com.red_escuela.backend.platform.infrastructure;

import com.red_escuela.backend.platform.domain.BackupStatusSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FileBackupStatusSourceTest {

    @TempDir
    Path directory;

    private Optional<BackupStatusSnapshot> readFile(String content) throws IOException {
        Path file = directory.resolve("status.json");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return new FileBackupStatusSource(file.toString()).read();
    }

    @Test
    void missingFileIsEmpty() {
        assertThat(new FileBackupStatusSource(directory.resolve("absent.json").toString()).read()).isEmpty();
    }

    @Test
    void aDirectoryInsteadOfAFileIsEmpty() {
        assertThat(new FileBackupStatusSource(directory.toString()).read()).isEmpty();
    }

    @Test
    void corruptJsonIsEmpty() throws IOException {
        assertThat(readFile("{ esto no es json")).isEmpty();
    }

    @Test
    void emptyFileIsEmpty() throws IOException {
        assertThat(readFile("")).isEmpty();
    }

    @Test
    void validFileIsRead() throws IOException {
        assertThat(readFile(BackupStatusFileParserTest.validJson())).isPresent();
    }

    @Test
    void fileThatDoesNotValidateAgainstTheSchemaIsEmpty() throws IOException {
        assertThat(readFile(BackupStatusFileParserTest.validJson().replace("\"schemaVersion\": 1", "\"schemaVersion\": 2"))).isEmpty();
    }

    @Test
    void fileLargerThanOneMebibyteIsEmpty() throws IOException {
        assertThat(readFile(BackupStatusFileParserTest.validJson() + " ".repeat(1024 * 1024 + 1))).isEmpty();
    }
}
