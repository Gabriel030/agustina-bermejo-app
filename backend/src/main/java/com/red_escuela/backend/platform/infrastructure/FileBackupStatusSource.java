package com.red_escuela.backend.platform.infrastructure;

import com.red_escuela.backend.platform.domain.BackupStatusSnapshot;
import com.red_escuela.backend.platform.domain.BackupStatusSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@Component
public class FileBackupStatusSource implements BackupStatusSource {

    private static final Logger LOG = LoggerFactory.getLogger(FileBackupStatusSource.class);
    private static final long MAX_FILE_BYTES = 1024 * 1024;

    private final Path statusFile;

    public FileBackupStatusSource(@Value("${app.backup.status-file:/backup-status/status.json}") String statusFile) {
        this.statusFile = Path.of(statusFile);
    }

    @Override
    public Optional<BackupStatusSnapshot> read() {
        try {
            if (!Files.isRegularFile(statusFile)) {
                LOG.warn("Archivo de estado de backups ausente: {}", statusFile);
                return Optional.empty();
            }
            if (Files.size(statusFile) > MAX_FILE_BYTES) {
                LOG.warn("Archivo de estado de backups descartado por superar {} bytes: {}", MAX_FILE_BYTES, statusFile);
                return Optional.empty();
            }
            try (InputStream content = Files.newInputStream(statusFile)) {
                return Optional.of(BackupStatusFileParser.parse(content));
            }
        } catch (IOException | RuntimeException e) {
            LOG.warn("Archivo de estado de backups ilegible o inválido ({}): {}", statusFile, e.getMessage());
            return Optional.empty();
        }
    }
}
