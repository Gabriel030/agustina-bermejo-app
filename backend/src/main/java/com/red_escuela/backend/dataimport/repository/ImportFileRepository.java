package com.red_escuela.backend.dataimport.repository;

import com.red_escuela.backend.dataimport.domain.ImportEntityType;
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
public interface ImportFileRepository extends JpaRepository<ImportFile, Long> {
    List<ImportFile> findByBatchIdOrderByEntityType(Long batchId);

    Optional<ImportFile> findByBatchIdAndEntityType(Long batchId, ImportEntityType entityType);
}
