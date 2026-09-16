package com.red_escuela.backend.service;

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

    // `carpeta` agrupa el archivo dentro del directorio del alumno (hoy: el nombre
    // de un DocumentType para documentos de alta, o "comprobantes" para
    // PaymentReceipt) — generalizado desde un DocumentType tipado porque un
    // comprobante de pago no es un "documento de alumno" en el mismo sentido, no
    // tiene por qué acoplar este service a ese enum (ver Deuda técnica / spec
    // técnica de Épica 7 Fase 3).
    public String store(Long studentId, String carpeta, MultipartFile file) {
        String extension = ALLOWED_CONTENT_TYPES.get(file.getContentType());
        if (extension == null) {
            throw new UnsupportedFileTypeException(file.getContentType());
        }

        Path studentDirectory = uploadsDirectory.resolve("students").resolve(studentId.toString());
        try {
            Files.createDirectories(studentDirectory);
            String fileName = carpeta + "_" + System.currentTimeMillis() + "." + extension;
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
        try {
            Files.createDirectories(dir);
            Path dest = dir.resolve(personId + "." + extension);
            file.transferTo(dest);
            return uploadsDirectory.relativize(dest).toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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
            Path dest = dir.resolve("qr." + extension);
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
