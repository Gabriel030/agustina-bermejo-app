package com.red_escuela.backend.service;

import com.red_escuela.backend.exception.UnsupportedFileTypeException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

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

    public String store(Long studentId, String carpeta, MultipartFile file) {
        String extension = ALLOWED_CONTENT_TYPES.get(file.getContentType());
        if (extension == null) {
            throw new UnsupportedFileTypeException(file.getContentType());
        }

        Path studentDirectory = uploadsDirectory.resolve("students").resolve(studentId.toString());
        try {
            Files.createDirectories(studentDirectory);
            String fileName = carpeta + "_" + UUID.randomUUID() + "." + extension;
            Path destination = studentDirectory.resolve(fileName);
            file.transferTo(destination);
            return uploadsDirectory.relativize(destination).toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String storeAvatar(Long personId, MultipartFile file) {
        String extension = switch (file.getContentType()) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            default -> throw new UnsupportedFileTypeException(file.getContentType());
        };
        Path dir = uploadsDirectory.resolve("avatars");
        try (InputStream content = file.getInputStream()) {
            Files.createDirectories(dir);
            Path dest = dir.resolve(personId + "_" + UUID.randomUUID() + "." + extension);
            Files.copy(content, dest, StandardCopyOption.REPLACE_EXISTING);
            return uploadsDirectory.relativize(dest).toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public boolean exists(String relativePath) {
        return Files.isRegularFile(uploadsDirectory.resolve(relativePath));
    }

    public byte[] readBytes(String relativePath) {
        try {
            return Files.readAllBytes(uploadsDirectory.resolve(relativePath));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String storeQr(MultipartFile file) {
        String extension = switch (file.getContentType()) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            default -> throw new UnsupportedFileTypeException(file.getContentType());
        };
        Path dir = uploadsDirectory.resolve("payment-gateway");
        try {
            Files.createDirectories(dir);
            Path dest = dir.resolve("qr_" + UUID.randomUUID() + "." + extension);
            file.transferTo(dest);
            return uploadsDirectory.relativize(dest).toString();
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
