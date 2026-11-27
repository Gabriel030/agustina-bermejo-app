package com.red_escuela.backend.attendance.repository;

import com.red_escuela.backend.attendance.entity.AttendanceChangeRequest;
import com.red_escuela.backend.attendance.entity.AttendanceChangeRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface AttendanceChangeRequestRepository extends JpaRepository<AttendanceChangeRequest, Long> {

    List<AttendanceChangeRequest> findByStatus(AttendanceChangeRequestStatus status);

    Page<AttendanceChangeRequest> findByStatusIn(Collection<AttendanceChangeRequestStatus> statuses, Pageable pageable);

    List<AttendanceChangeRequest> findByRequestedByIdOrderByCreatedAtDesc(Long instructorId);
}
