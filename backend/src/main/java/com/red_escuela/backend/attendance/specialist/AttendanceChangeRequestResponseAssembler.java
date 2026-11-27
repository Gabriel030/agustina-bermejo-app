package com.red_escuela.backend.attendance.specialist;

import com.red_escuela.backend.attendance.dto.AttendanceChangeRequestResponse;
import com.red_escuela.backend.attendance.entity.AttendanceChangeRequest;
import com.red_escuela.backend.identity.entity.Person;
import org.springframework.stereotype.Component;

@Component
public class AttendanceChangeRequestResponseAssembler {

    public AttendanceChangeRequestResponse assemble(AttendanceChangeRequest request) {
        var attendance = request.getAttendance();
        Person studentPerson;
        String subjectName;
        String sectionName;
        if (attendance.getProgramSubjectSectionEnrollment() != null) {
            var enrollment = attendance.getProgramSubjectSectionEnrollment();
            studentPerson = enrollment.getStudent().getPerson();
            subjectName = enrollment.getSubjectSection().getSubject().getName();
            sectionName = enrollment.getSubjectSection().getName();
        } else {
            var enrollment = attendance.getCourseSubjectSectionEnrollment();
            studentPerson = enrollment.getStudent().getPerson();
            subjectName = enrollment.getCourseSubjectSection().getCourseSubject().getName();
            sectionName = enrollment.getCourseSubjectSection().getName();
        }
        Person requestedByPerson = request.getRequestedBy().getPerson();
        Person decidedByPerson = request.getApprovedBy();
        return AttendanceChangeRequestResponse.from(
                request, fullName(requestedByPerson), subjectName, sectionName,
                attendance.getClassDate(), fullName(studentPerson),
                decidedByPerson == null ? null : fullName(decidedByPerson));
    }

    private String fullName(Person person) {
        return person.getName() + " " + person.getLastname();
    }
}
