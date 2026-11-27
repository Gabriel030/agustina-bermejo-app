package com.red_escuela.backend.academicstructure.repository;

import com.red_escuela.backend.academicstructure.entity.Program;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProgramRepository extends JpaRepository<Program, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Program p where p.id = :id")
    java.util.Optional<Program> findByIdForUpdate(@Param("id") Long id);

    java.util.Optional<Program> findFirstByName(String name);

    @Query("select new com.red_escuela.backend.academicstructure.repository.NamedId(p.name, p.id) from Program p where p.name in :names order by p.id")
    java.util.List<NamedId> findNamedIdsByNameIn(@Param("names") java.util.Collection<String> names);
}
