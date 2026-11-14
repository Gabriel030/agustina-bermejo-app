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
public interface ImportCreatedEntityRepository extends JpaRepository<ImportCreatedEntity, Long> {
    long countByBatchId(Long batchId);

    List<ImportCreatedEntity> findByBatchIdOrderByIdDesc(Long batchId);

    @Query("""
            select new com.red_escuela.backend.dataimport.repository.ImportCreatedCount(c.entityType, count(c))
            from ImportCreatedEntity c where c.batchId = :batchId group by c.entityType
            """)
    List<ImportCreatedCount> countByEntityType(@Param("batchId") Long batchId);

    @Modifying(flushAutomatically = true)
    @Query("delete from ImportCreatedEntity c where c.batchId = :batchId and c.entityType not in :retained")
    void deleteByBatchIdAndEntityTypeNotIn(@Param("batchId") Long batchId, @Param("retained") Collection<ImportEntityType> retained);
}
