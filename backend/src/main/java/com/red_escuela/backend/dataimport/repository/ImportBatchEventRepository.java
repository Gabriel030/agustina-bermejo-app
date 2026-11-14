package com.red_escuela.backend.dataimport.repository;

import com.red_escuela.backend.dataimport.entity.*;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ImportBatchEventRepository extends JpaRepository<ImportBatchEvent, Long> {
    List<ImportBatchEvent> findByBatchIdOrderById(Long batchId);
}
