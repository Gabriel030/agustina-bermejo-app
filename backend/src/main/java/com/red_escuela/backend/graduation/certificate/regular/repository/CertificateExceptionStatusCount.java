package com.red_escuela.backend.graduation.certificate.regular.repository;

import com.red_escuela.backend.graduation.certificate.regular.entity.CertificateExceptionStatus;

public record CertificateExceptionStatusCount(CertificateExceptionStatus status, long total) {
}
