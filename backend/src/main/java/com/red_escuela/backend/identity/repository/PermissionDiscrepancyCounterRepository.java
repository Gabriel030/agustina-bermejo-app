package com.red_escuela.backend.identity.repository;

import com.red_escuela.backend.identity.entity.PermissionDiscrepancyCounter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface PermissionDiscrepancyCounterRepository extends JpaRepository<PermissionDiscrepancyCounter, Long> {

    @Modifying
    @Query(value = """
            insert into permission_discrepancy_counter (role, module, permission_id, occurrences, last_seen_at)
            values (:role, :module, :permissionId, :occurrences, :seenAt)
            on conflict (role, permission_id) do update
            set occurrences = permission_discrepancy_counter.occurrences + excluded.occurrences,
                last_seen_at = greatest(permission_discrepancy_counter.last_seen_at, excluded.last_seen_at)
            """, nativeQuery = true)
    void addOccurrences(@Param("role") String role, @Param("module") String module,
                        @Param("permissionId") Long permissionId, @Param("occurrences") long occurrences,
                        @Param("seenAt") Instant seenAt);

    @Query(value = """
            select c.role as role, c.module as module, p.code as permissionCode,
                   c.occurrences as occurrences, c.lastSeenAt as lastSeenAt
            from PermissionDiscrepancyCounter c join Permission p on p.id = c.permissionId
            order by c.lastSeenAt desc, c.id desc
            """, countQuery = "select count(c) from PermissionDiscrepancyCounter c")
    Page<PermissionDiscrepancyRow> findPageOrderedByLastSeen(Pageable pageable);
}
