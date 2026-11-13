package com.red_escuela.backend.graduation.certificate.regular.specialist;

import com.red_escuela.backend.identity.entity.Student;

public record RegularEnrollmentFacts(Student student, String titleKind, String titleName) {

    public String studentName() {
        return student.getPerson().getName() + " " + student.getPerson().getLastname();
    }
}
