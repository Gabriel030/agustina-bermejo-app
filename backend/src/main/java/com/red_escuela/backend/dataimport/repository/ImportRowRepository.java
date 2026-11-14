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
public interface ImportRowRepository extends JpaRepository<ImportRow, Long> {
    List<ImportRow> findByBatchIdAndEntityTypeOrderByRowNumber(Long batchId, ImportEntityType entityType);

    List<ImportRow> findByBatchIdAndEntityTypeAndStatusOrderByRowNumber(Long batchId, ImportEntityType entityType,
            ImportRowStatus status, Pageable pageable);

    List<ImportRow> findByBatchIdAndStatusInOrderByEntityTypeAscRowNumberAsc(Long batchId,
            Collection<ImportRowStatus> statuses, Pageable pageable);

    long countByBatchIdAndStatusIn(Long batchId, Collection<ImportRowStatus> statuses);

    long countByBatchIdAndEntityTypeAndNaturalKeyStartingWith(Long batchId, ImportEntityType entityType, String prefix);

    boolean existsByBatchIdAndEntityTypeAndNaturalKey(Long batchId, ImportEntityType entityType, String naturalKey);

    boolean existsByEntityTypeAndNaturalKeyAndContentHashAndStatus(ImportEntityType entityType, String naturalKey,
            String contentHash, ImportRowStatus status);

    @Modifying(flushAutomatically = true)
    @Query("delete from ImportRow r where r.batchId = :batchId and r.entityType = :entityType")
    void deleteByBatchIdAndEntityType(@Param("batchId") Long batchId, @Param("entityType") ImportEntityType entityType);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ImportRow r set r.status = :to, r.errors = null, r.committedEntityId = null where r.batchId = :batchId and r.status = :from")
    int moveStatus(@Param("batchId") Long batchId, @Param("from") ImportRowStatus from, @Param("to") ImportRowStatus to);

    List<ImportRow> findByBatchIdOrderByEntityTypeAscRowNumberAsc(Long batchId, Pageable pageable);

    @Query("""
            select new com.red_escuela.backend.dataimport.repository.ImportRowStatusCount(r.entityType, r.status, count(r))
            from ImportRow r where r.batchId = :batchId group by r.entityType, r.status
            """)
    List<ImportRowStatusCount> countByStatus(@Param("batchId") Long batchId);
}
