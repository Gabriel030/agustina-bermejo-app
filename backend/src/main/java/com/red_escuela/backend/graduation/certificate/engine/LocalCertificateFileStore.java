package com.red_escuela.backend.graduation.certificate.engine;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@Component
public class LocalCertificateFileStore implements CertificateFileStore {

    private final Path root;

    public LocalCertificateFileStore(@Value("${app.certificates.directory}") String directory) {
        this.root = Path.of(directory).toAbsolutePath().normalize();
    }

    @Override
    public String save(String code, byte[] pdf) {
        String fileName = new CertificateCode(code).value() + ".pdf";
        try {
            Files.createDirectories(root);
            Files.write(root.resolve(fileName), pdf);
            return fileName;
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar el PDF del certificado", e);
        }
    }

    @Override
    public Optional<byte[]> load(String path) {
        Path file = resolve(path);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(file));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el PDF del certificado", e);
        }
    }

    @Override
    public void deleteIfRolledBack(String path) {
        try {
            Files.deleteIfExists(resolve(path));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo borrar el PDF del certificado", e);
        }
    }

    private Path resolve(String path) {
        Path file = root.resolve(path).normalize();
        if (!file.startsWith(root)) {
            throw new IllegalArgumentException("Ruta de certificado fuera del directorio permitido");
        }
        return file;
    }
}
