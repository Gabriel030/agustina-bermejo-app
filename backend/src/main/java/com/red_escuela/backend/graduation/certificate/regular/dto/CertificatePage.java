package com.red_escuela.backend.graduation.certificate.regular.dto;

import java.util.List;

public record CertificatePage<T>(List<T> items, long totalElements) {
}
