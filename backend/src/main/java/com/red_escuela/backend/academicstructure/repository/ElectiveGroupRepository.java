package com.red_escuela.backend.academicstructure.repository;

import com.red_escuela.backend.academicstructure.entity.ElectiveGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ElectiveGroupRepository extends JpaRepository<ElectiveGroup, Long> {

    Optional<ElectiveGroup> findByName(String name);
}
