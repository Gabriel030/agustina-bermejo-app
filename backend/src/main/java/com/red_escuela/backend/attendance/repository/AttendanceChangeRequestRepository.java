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

@Repository
public interface AttendanceChangeRequestRepository extends JpaRepository<AttendanceChangeRequest, Long> {

    @Query(value = """
            select r from AttendanceChangeRequest r
            join r.attendance a
            left join a.programSubjectSectionEnrollment pe
            left join pe.programEnrollment pge
            left join pge.program pr
            left join a.courseSubjectSectionEnrollment ce
            left join ce.courseEnrollment cge
            left join cge.course co
            where r.status in :statuses
              and (:unrestricted = true or pr.id in :programIds or co.id in :courseIds)
            """,
            countQuery = """
            select count(r) from AttendanceChangeRequest r
            join r.attendance a
            left join a.programSubjectSectionEnrollment pe
            left join pe.programEnrollment pge
            left join pge.program pr
            left join a.courseSubjectSectionEnrollment ce
            left join ce.courseEnrollment cge
            left join cge.course co
            where r.status in :statuses
              and (:unrestricted = true or pr.id in :programIds or co.id in :courseIds)
            """)
    Page<AttendanceChangeRequest> findInScope(@Param("statuses") Collection<AttendanceChangeRequestStatus> statuses,
                                              @Param("unrestricted") boolean unrestricted,
                                              @Param("programIds") Collection<Long> programIds,
                                              @Param("courseIds") Collection<Long> courseIds,
                                              Pageable pageable);

    List<AttendanceChangeRequest> findByRequestedByIdOrderByCreatedAtDesc(Long instructorId);
}
