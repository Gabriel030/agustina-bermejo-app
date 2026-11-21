package com.red_escuela.backend.graduation.certificate.signatory.dto;

public record SignatorySignatureUpload(byte[] content, String contentType, String originalFilename) {
}
