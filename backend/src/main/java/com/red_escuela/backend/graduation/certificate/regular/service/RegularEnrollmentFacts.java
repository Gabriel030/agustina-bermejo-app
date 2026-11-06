package com.red_escuela.backend.graduation.certificate.regular.service;

import com.red_escuela.backend.identity.entity.Student;

public record RegularEnrollmentFacts(Student student, String titleKind, String titleName) {
}
