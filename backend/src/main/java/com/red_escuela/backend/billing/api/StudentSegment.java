package com.red_escuela.backend.billing.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

public interface StudentSegment {

    String name();

    List<StudentSegmentParameter> parameters(LocalDate asOf);

    Page<StudentSegmentMember> findMembers(LocalDate asOf, Pageable pageable);
}
