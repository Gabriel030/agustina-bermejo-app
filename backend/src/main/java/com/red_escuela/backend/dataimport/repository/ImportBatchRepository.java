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
public interface ImportBatchRepository extends JpaRepository<ImportBatch, Long> {
    boolean existsByInstitutionIdAndStatusIn(Long institutionId, Collection<ImportBatchStatus> statuses);

    List<ImportBatch> findAllByOrderByIdDesc();
}
