package com.red_escuela.backend.identity.dto;

import com.red_escuela.backend.identity.entity.Student;

public record EmergencyContactResponse(String name, String relationship, String phone) {

    public static EmergencyContactResponse from(Student student) {
        if (student.getEmergencyContactName() == null && student.getEmergencyContactRelationship() == null
                && student.getEmergencyContactPhone() == null) {
            return null;
        }
        return new EmergencyContactResponse(student.getEmergencyContactName(),
                student.getEmergencyContactRelationship(), student.getEmergencyContactPhone());
    }
}
