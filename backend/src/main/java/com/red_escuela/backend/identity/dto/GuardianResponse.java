package com.red_escuela.backend.identity.dto;

import com.red_escuela.backend.identity.entity.Student;

public record GuardianResponse(String fullName, String documentNumber, String relationship, String phone, String email) {

    public static GuardianResponse from(Student student) {
        if (student.getGuardianFullName() == null && student.getGuardianDni() == null
                && student.getGuardianRelationship() == null && student.getGuardianPhone() == null
                && student.getGuardianEmail() == null) {
            return null;
        }
        return new GuardianResponse(student.getGuardianFullName(), student.getGuardianDni(),
                student.getGuardianRelationship(), student.getGuardianPhone(), student.getGuardianEmail());
    }
}
