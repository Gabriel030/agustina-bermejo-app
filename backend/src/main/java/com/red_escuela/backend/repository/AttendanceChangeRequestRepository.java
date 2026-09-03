package com.red_escuela.backend.repository;

import com.red_escuela.backend.entity.AttendanceChangeRequest;
import com.red_escuela.backend.entity.AttendanceChangeRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AttendanceChangeRequestRepository extends JpaRepository<AttendanceChangeRequest, Long> {

    List<AttendanceChangeRequest> findByStatus(AttendanceChangeRequestStatus status);

    List<AttendanceChangeRequest> findByRequestedByIdOrderByCreatedAtDesc(Long instructorId);
}
