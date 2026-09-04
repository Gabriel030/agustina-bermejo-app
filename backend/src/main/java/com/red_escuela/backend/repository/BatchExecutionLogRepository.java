package com.red_escuela.backend.repository;

import com.red_escuela.backend.entity.BatchExecutionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BatchExecutionLogRepository extends JpaRepository<BatchExecutionLog, Long> {
    List<BatchExecutionLog> findByJobNameOrderByRanAtDesc(String jobName);
}
