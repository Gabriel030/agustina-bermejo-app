package com.red_escuela.backend.service;

import com.red_escuela.backend.entity.DocumentType;
import com.red_escuela.backend.exception.UnsupportedFileTypeException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Responsabilidad única: validar y persistir un MultipartFile a disco, devolver la
 * ruta relativa guardada. La ruta devuelta es una referencia opaca para el caller
 * (no asume que el storage siga siendo siempre filesystem — ver deuda técnica de la
 * spec: el día que se containerice la app o se migre a object storage, esta interfaz
 * no debería tener que cambiar).
 */
@Service
public class FileStorageService {

    private static final Map<String, String> ALLOWED_CONTENT_TYPES = Map.of(
            "application/pdf", "pdf",
            "image/jpeg", "jpg",
            "image/png", "png"
    );

    private final Path uploadsDirectory;

    public FileStorageService(@Value("${app.uploads.directory}") String uploadsDirectory) {
        this.uploadsDirectory = Path.of(uploadsDirectory);
    }

    public String store(Long studentId, DocumentType type, MultipartFile file) {
        String extension = ALLOWED_CONTENT_TYPES.get(file.getContentType());
        if (extension == null) {
            throw new UnsupportedFileTypeException(file.getContentType());
        }

        Path studentDirectory = uploadsDirectory.resolve("students").resolve(studentId.toString());
        try {
            Files.createDirectories(studentDirectory);
            String fileName = type + "_" + System.currentTimeMillis() + "." + extension;
            Path destination = studentDirectory.resolve(fileName);
            file.transferTo(destination);
            return uploadsDirectory.relativize(destination).toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void delete(String relativePath) {
        try {
            Files.deleteIfExists(uploadsDirectory.resolve(relativePath));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
