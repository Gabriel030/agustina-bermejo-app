package com.red_escuela.backend.attendance.repository;

import com.red_escuela.backend.attendance.entity.AttendanceChangeRequest;
import com.red_escuela.backend.attendance.entity.AttendanceChangeRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceChangeRequestRepository extends JpaRepository<AttendanceChangeRequest, Long> {

    @Query(value = """
            select r from AttendanceChangeRequest r
            join r.attendance a
            left join a.programSubjectSectionEnrollment pe
            left join pe.subjectSection ss
            left join pe.programEnrollment pge
            left join pge.programCohort pc
            left join a.courseSubjectSectionEnrollment ce
            left join ce.courseSubjectSection cs
            where r.status in :statuses
              and (:unrestricted = true or ss.id in :subjectSectionIds or cs.id in :courseSectionIds or pc.id in :programCohortIds)
            """,
            countQuery = """
            select count(r) from AttendanceChangeRequest r
            join r.attendance a
            left join a.programSubjectSectionEnrollment pe
            left join pe.subjectSection ss
            left join pe.programEnrollment pge
            left join pge.programCohort pc
            left join a.courseSubjectSectionEnrollment ce
            left join ce.courseSubjectSection cs
            where r.status in :statuses
              and (:unrestricted = true or ss.id in :subjectSectionIds or cs.id in :courseSectionIds or pc.id in :programCohortIds)
            """)
    Page<AttendanceChangeRequest> findInScope(@Param("statuses") Collection<AttendanceChangeRequestStatus> statuses,
                                              @Param("unrestricted") boolean unrestricted,
                                              @Param("subjectSectionIds") Collection<Long> subjectSectionIds,
                                              @Param("courseSectionIds") Collection<Long> courseSectionIds,
                                              @Param("programCohortIds") Collection<Long> programCohortIds,
                                              Pageable pageable);

    @Query("""
            select r from AttendanceChangeRequest r
            join r.attendance a
            left join a.programSubjectSectionEnrollment pe
            left join pe.subjectSection ss
            left join pe.programEnrollment pge
            left join pge.programCohort pc
            left join a.courseSubjectSectionEnrollment ce
            left join ce.courseSubjectSection cs
            where r.id = :id
              and (:unrestricted = true or ss.id in :subjectSectionIds or cs.id in :courseSectionIds or pc.id in :programCohortIds)
            """)
    Optional<AttendanceChangeRequest> findByIdInScope(@Param("id") Long id,
                                                      @Param("unrestricted") boolean unrestricted,
                                                      @Param("subjectSectionIds") Collection<Long> subjectSectionIds,
                                                      @Param("courseSectionIds") Collection<Long> courseSectionIds,
                                                      @Param("programCohortIds") Collection<Long> programCohortIds);

    List<AttendanceChangeRequest> findByRequestedByIdOrderByCreatedAtDesc(Long instructorId);
}
