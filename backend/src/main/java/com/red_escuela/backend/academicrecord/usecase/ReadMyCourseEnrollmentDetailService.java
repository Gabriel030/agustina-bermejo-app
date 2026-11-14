package com.red_escuela.backend.academicrecord.usecase;

import com.red_escuela.backend.academicrecord.dto.MyCourseEnrollmentDetailResponse;
import com.red_escuela.backend.academicrecord.dto.StudentCourseSectionEnrollmentResponse;
import com.red_escuela.backend.academicrecord.specialist.AcademicRecordAttendanceFinder;
import com.red_escuela.backend.academicrecord.specialist.AcademicRecordCourseEnrollmentFinder;
import com.red_escuela.backend.academicrecord.specialist.AcademicRecordCourseSectionEnrollmentFinder;
import com.red_escuela.backend.academicrecord.specialist.AcademicRecordGradeItemFinder;
import com.red_escuela.backend.enrollment.api.CourseEnrollmentDetailView;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ReadMyCourseEnrollmentDetailService implements ReadMyCourseEnrollmentDetailUseCase {

    private final AcademicRecordCourseEnrollmentFinder courseEnrollmentFinder;
    private final AcademicRecordCourseSectionEnrollmentFinder courseSectionEnrollmentFinder;
    private final AcademicRecordAttendanceFinder attendanceFinder;
    private final AcademicRecordGradeItemFinder gradeItemFinder;

    public ReadMyCourseEnrollmentDetailService(
            AcademicRecordCourseEnrollmentFinder courseEnrollmentFinder,
            AcademicRecordCourseSectionEnrollmentFinder courseSectionEnrollmentFinder,
            AcademicRecordAttendanceFinder attendanceFinder,
            AcademicRecordGradeItemFinder gradeItemFinder) {
        this.courseEnrollmentFinder = courseEnrollmentFinder;
        this.courseSectionEnrollmentFinder = courseSectionEnrollmentFinder;
        this.attendanceFinder = attendanceFinder;
        this.gradeItemFinder = gradeItemFinder;
    }

    @Override
    public MyCourseEnrollmentDetailResponse read(Long studentId, Long courseEnrollmentId) {
        CourseEnrollmentDetailView enrollment = courseEnrollmentFinder.readOfStudent(courseEnrollmentId, studentId);
        List<MyCourseEnrollmentDetailResponse.Subject> subjects =
                courseSectionEnrollmentFinder.listOfStudent(studentId, courseEnrollmentId).stream()
                        .map(this::subjectOf)
                        .toList();
        return MyCourseEnrollmentDetailResponse.from(enrollment, subjects);
    }

    private MyCourseEnrollmentDetailResponse.Subject subjectOf(StudentCourseSectionEnrollmentResponse sectionEnrollment) {
        Long id = sectionEnrollment.id();
        return new MyCourseEnrollmentDetailResponse.Subject(
                sectionEnrollment,
                attendanceFinder.readCourseEnrollmentRisk(id),
                attendanceFinder.listCourseEnrollmentAttendance(id),
                gradeItemFinder.listCourseEnrollmentGradeItems(id));
    }
}
