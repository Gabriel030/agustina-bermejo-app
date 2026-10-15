package com.red_escuela.backend.attendance.repository;

import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    List<Attendance> findByProgramSubjectSectionEnrollmentId(Long enrollmentId);

    @Query("""
            select a.programSubjectSectionEnrollment.id, count(a),
                   sum(case when a.status = :absent then 1 else 0 end)
            from Attendance a
            where a.programSubjectSectionEnrollment.id in :enrollmentIds
            group by a.programSubjectSectionEnrollment.id
            """)
    List<Object[]> countsByProgramEnrollment(@Param("enrollmentIds") List<Long> enrollmentIds,
                                             @Param("absent") AttendanceStatus absent);

    @Query("""
            select a.courseSubjectSectionEnrollment.id, count(a),
                   sum(case when a.status = :absent then 1 else 0 end)
            from Attendance a
            where a.courseSubjectSectionEnrollment.id in :enrollmentIds
            group by a.courseSubjectSectionEnrollment.id
            """)
    List<Object[]> countsByCourseEnrollment(@Param("enrollmentIds") List<Long> enrollmentIds,
                                            @Param("absent") AttendanceStatus absent);

    @Query("""
            select a.programSubjectSectionEnrollment.id as enrollmentId, count(a) as total,
                   sum(case when a.status in :presentLike then 1 else 0 end) as present,
                   sum(case when a.status in :absences then 1 else 0 end) as absences
            from Attendance a
            where a.programSubjectSectionEnrollment.subjectSection.id = :sectionId
            group by a.programSubjectSectionEnrollment.id
            """)
    List<EnrollmentAttendanceCounts> countsByProgramSection(@Param("sectionId") Long sectionId,
                                                           @Param("presentLike") List<AttendanceStatus> presentLike,
                                                           @Param("absences") List<AttendanceStatus> absences);

    @Query("""
            select a.courseSubjectSectionEnrollment.id as enrollmentId, count(a) as total,
                   sum(case when a.status in :presentLike then 1 else 0 end) as present,
                   sum(case when a.status in :absences then 1 else 0 end) as absences
            from Attendance a
            where a.courseSubjectSectionEnrollment.courseSubjectSection.id = :sectionId
            group by a.courseSubjectSectionEnrollment.id
            """)
    List<EnrollmentAttendanceCounts> countsByCourseSection(@Param("sectionId") Long sectionId,
                                                          @Param("presentLike") List<AttendanceStatus> presentLike,
                                                          @Param("absences") List<AttendanceStatus> absences);

    List<Attendance> findByCourseSubjectSectionEnrollmentId(Long enrollmentId);

    Optional<Attendance> findByProgramSubjectSectionEnrollmentIdAndClassDate(Long enrollmentId, LocalDate classDate);

    Optional<Attendance> findByCourseSubjectSectionEnrollmentIdAndClassDate(Long enrollmentId, LocalDate classDate);

    long countByProgramSubjectSectionEnrollmentId(Long enrollmentId);

    long countByCourseSubjectSectionEnrollmentId(Long enrollmentId);

    long countByProgramSubjectSectionEnrollmentIdAndStatus(Long enrollmentId, AttendanceStatus status);

    long countByCourseSubjectSectionEnrollmentIdAndStatus(Long enrollmentId, AttendanceStatus status);

    long countByProgramSubjectSectionEnrollmentIdAndStatusIn(Long enrollmentId, List<AttendanceStatus> statuses);

    long countByCourseSubjectSectionEnrollmentIdAndStatusIn(Long enrollmentId, List<AttendanceStatus> statuses);

    List<Attendance> findByProgramSubjectSectionEnrollment_SubjectSection_Id(Long subjectSectionId);

    List<Attendance> findByCourseSubjectSectionEnrollment_CourseSubjectSection_Id(Long courseSubjectSectionId);
}
