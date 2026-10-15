package com.red_escuela.backend.attendance.usecase.attendancechangerequest;

import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.entity.AttendanceChangeRequest;
import com.red_escuela.backend.attendance.entity.AttendanceChangeRequestStatus;
import com.red_escuela.backend.attendance.exception.AttendanceNotFoundException;
import com.red_escuela.backend.attendance.repository.AttendanceChangeRequestRepository;
import com.red_escuela.backend.attendance.repository.AttendanceRepository;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.evaluation.exception.NotOwnSectionException;
import com.red_escuela.backend.academicstructure.repository.CourseSubjectSectionInstructorRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionInstructorRepository;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class RequestAttendanceChangeService implements RequestAttendanceChangeUseCase {

    private final AttendanceChangeRequestRepository attendanceChangeRequestRepository;
    private final AttendanceRepository attendanceRepository;
    private final SubjectSectionInstructorRepository subjectSectionInstructorRepository;
    private final CourseSubjectSectionInstructorRepository courseSubjectSectionInstructorRepository;

    public RequestAttendanceChangeService(AttendanceChangeRequestRepository attendanceChangeRequestRepository,
                                           AttendanceRepository attendanceRepository,
                                           SubjectSectionInstructorRepository subjectSectionInstructorRepository,
                                           CourseSubjectSectionInstructorRepository courseSubjectSectionInstructorRepository) {
        this.attendanceChangeRequestRepository = attendanceChangeRequestRepository;
        this.attendanceRepository = attendanceRepository;
        this.subjectSectionInstructorRepository = subjectSectionInstructorRepository;
        this.courseSubjectSectionInstructorRepository = courseSubjectSectionInstructorRepository;
    }

    @Override
    @Transactional
    public AttendanceChangeRequest request(RequestAttendanceChangeCommand command) {
        Attendance attendance = attendanceRepository.findById(command.attendanceId())
                .orElseThrow(() -> new AttendanceNotFoundException(command.attendanceId()));
        Long sectionId = resolveSectionId(attendance);
        Long requestedById = command.requestedBy().getId();
        boolean esProgramSection = attendance.getProgramSubjectSectionEnrollment() != null;
        boolean esPropia = esProgramSection
                ? subjectSectionInstructorRepository.existsBySectionIdAndInstructorId(sectionId, requestedById)
                : courseSubjectSectionInstructorRepository.existsBySectionIdAndInstructorId(sectionId, requestedById);
        if (!esPropia) {
            throw new NotOwnSectionException(requestedById, sectionId);
        }

        command.body().requestedStatus().requireRecordableByInstructor();
        AttendanceChangeRequest request = new AttendanceChangeRequest();
        request.setAttendance(attendance);
        request.setRequestedBy(command.requestedBy());
        request.setPreviousStatus(attendance.getStatus());
        request.setRequestedStatus(command.body().requestedStatus());
        request.setReason(command.body().reason());
        request.setStatus(AttendanceChangeRequestStatus.PENDING);
        return attendanceChangeRequestRepository.save(request);
    }

    private Long resolveSectionId(Attendance attendance) {
        if (attendance.getProgramSubjectSectionEnrollment() != null) {
            SubjectSectionEnrollment enrollment = attendance.getProgramSubjectSectionEnrollment();
            return enrollment.getSubjectSection().getId();
        }
        CourseSubjectSectionEnrollment enrollment = attendance.getCourseSubjectSectionEnrollment();
        return enrollment.getCourseSubjectSection().getId();
    }
}
