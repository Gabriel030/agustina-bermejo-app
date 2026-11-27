package com.red_escuela.backend.attendance.repository;

import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    List<Attendance> findByProgramSubjectSectionEnrollmentId(Long enrollmentId);

    boolean existsByProgramSubjectSectionEnrollmentId(Long enrollmentId);

    @Query("select distinct a.classDate from Attendance a where a.classDate in :dates order by a.classDate")
    List<LocalDate> findDistinctClassDatesIn(@Param("dates") Collection<LocalDate> dates);

    @Query("select a.justificationAttachmentUrl from Attendance a where a.id = :attendanceId and a.justificationAttachmentUrl is not null")
    Optional<String> findJustificationAttachmentKeyById(@Param("attendanceId") Long attendanceId);

    Page<Attendance> findByStatus(AttendanceStatus status, Pageable pageable);

    Page<Attendance> findByStatusAndJustificationReasonIsNotNull(AttendanceStatus status, Pageable pageable);

    Page<Attendance> findByStatusAndJustificationRejectionReasonIsNotNull(AttendanceStatus status, Pageable pageable);

    @Query("""
            select a.programSubjectSectionEnrollment.id as enrollmentId, count(a) as total,
                   sum(case when a.status in :presenceStatuses then 1 else 0 end) as present,
                   sum(case when a.status in :absenceStatuses then 1 else 0 end) as absences,
                   sum(case when a.status = :pendingStatus then 1 else 0 end) as pending
            from Attendance a
            where a.programSubjectSectionEnrollment.id in :enrollmentIds
              and not exists (select 1 from ClassSession cs where cs.subjectSection = a.programSubjectSectionEnrollment.subjectSection
                              and cs.classDate = a.classDate and cs.status = com.red_escuela.backend.attendance.entity.ClassSessionStatus.CANCELED)
            group by a.programSubjectSectionEnrollment.id
            """)
    List<EnrollmentAttendanceCounts> countsByProgramEnrollmentIds(@Param("enrollmentIds") java.util.Collection<Long> enrollmentIds,
                                           @Param("presenceStatuses") List<AttendanceStatus> presenceStatuses,
                                           @Param("absenceStatuses") List<AttendanceStatus> absenceStatuses,
                                           @Param("pendingStatus") AttendanceStatus pendingStatus);

    @Query("""
            select a.courseSubjectSectionEnrollment.id as enrollmentId, count(a) as total,
                   sum(case when a.status in :presenceStatuses then 1 else 0 end) as present,
                   sum(case when a.status in :absenceStatuses then 1 else 0 end) as absences,
                   sum(case when a.status = :pendingStatus then 1 else 0 end) as pending
            from Attendance a
            where a.courseSubjectSectionEnrollment.id in :enrollmentIds
              and not exists (select 1 from ClassSession cs where cs.courseSubjectSection = a.courseSubjectSectionEnrollment.courseSubjectSection
                              and cs.classDate = a.classDate and cs.status = com.red_escuela.backend.attendance.entity.ClassSessionStatus.CANCELED)
            group by a.courseSubjectSectionEnrollment.id
            """)
    List<EnrollmentAttendanceCounts> countsByCourseEnrollmentIds(@Param("enrollmentIds") java.util.Collection<Long> enrollmentIds,
                                           @Param("presenceStatuses") List<AttendanceStatus> presenceStatuses,
                                           @Param("absenceStatuses") List<AttendanceStatus> absenceStatuses,
                                           @Param("pendingStatus") AttendanceStatus pendingStatus);

    @Query("""
            select a.programSubjectSectionEnrollment.id as enrollmentId, count(a) as total,
                   sum(case when a.status in :presenceStatuses then 1 else 0 end) as present,
                   sum(case when a.status in :absenceStatuses then 1 else 0 end) as absences,
                   sum(case when a.status = :pendingStatus then 1 else 0 end) as pending
            from Attendance a
            where a.programSubjectSectionEnrollment.subjectSection.id = :sectionId
              and not exists (select 1 from ClassSession cs where cs.subjectSection = a.programSubjectSectionEnrollment.subjectSection
                              and cs.classDate = a.classDate and cs.status = com.red_escuela.backend.attendance.entity.ClassSessionStatus.CANCELED)
            group by a.programSubjectSectionEnrollment.id
            """)
    List<EnrollmentAttendanceCounts> countsByProgramSection(@Param("sectionId") Long sectionId,
                                           @Param("presenceStatuses") List<AttendanceStatus> presenceStatuses,
                                           @Param("absenceStatuses") List<AttendanceStatus> absenceStatuses,
                                           @Param("pendingStatus") AttendanceStatus pendingStatus);

    @Query("""
            select a.courseSubjectSectionEnrollment.id as enrollmentId, count(a) as total,
                   sum(case when a.status in :presenceStatuses then 1 else 0 end) as present,
                   sum(case when a.status in :absenceStatuses then 1 else 0 end) as absences,
                   sum(case when a.status = :pendingStatus then 1 else 0 end) as pending
            from Attendance a
            where a.courseSubjectSectionEnrollment.courseSubjectSection.id = :sectionId
              and not exists (select 1 from ClassSession cs where cs.courseSubjectSection = a.courseSubjectSectionEnrollment.courseSubjectSection
                              and cs.classDate = a.classDate and cs.status = com.red_escuela.backend.attendance.entity.ClassSessionStatus.CANCELED)
            group by a.courseSubjectSectionEnrollment.id
            """)
    List<EnrollmentAttendanceCounts> countsByCourseSection(@Param("sectionId") Long sectionId,
                                           @Param("presenceStatuses") List<AttendanceStatus> presenceStatuses,
                                           @Param("absenceStatuses") List<AttendanceStatus> absenceStatuses,
                                           @Param("pendingStatus") AttendanceStatus pendingStatus);

    List<Attendance> findByCourseSubjectSectionEnrollmentId(Long enrollmentId);

    Optional<Attendance> findByProgramSubjectSectionEnrollmentIdAndClassDate(Long enrollmentId, LocalDate classDate);

    Optional<Attendance> findByCourseSubjectSectionEnrollmentIdAndClassDate(Long enrollmentId, LocalDate classDate);

    List<Attendance> findByProgramSubjectSectionEnrollment_SubjectSection_Id(Long subjectSectionId);

    List<Attendance> findByCourseSubjectSectionEnrollment_CourseSubjectSection_Id(Long courseSubjectSectionId);

    List<Attendance> findByProgramSubjectSectionEnrollment_SubjectSection_IdAndClassDate(Long subjectSectionId, LocalDate classDate);

    List<Attendance> findByCourseSubjectSectionEnrollment_CourseSubjectSection_IdAndClassDate(Long courseSubjectSectionId, LocalDate classDate);

    boolean existsByProgramSubjectSectionEnrollment_SubjectSection_IdAndClassDate(Long subjectSectionId, LocalDate classDate);
}
