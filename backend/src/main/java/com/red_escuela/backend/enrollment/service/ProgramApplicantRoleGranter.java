package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.identity.api.StudentRoleGranter;
import com.red_escuela.backend.identity.entity.Student;
import org.springframework.stereotype.Component;

@Component
public class ProgramApplicantRoleGranter {
    private final StudentRoleGranter studentRoleGranter;

    public ProgramApplicantRoleGranter(StudentRoleGranter studentRoleGranter) {
        this.studentRoleGranter = studentRoleGranter;
    }

    public void grantStudentRole(Student student) {
        studentRoleGranter.grantStudentRoleIfMissing(student.getPerson());
    }
}
