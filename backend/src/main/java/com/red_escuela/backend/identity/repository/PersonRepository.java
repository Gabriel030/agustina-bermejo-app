package com.red_escuela.backend.identity.repository;

import com.red_escuela.backend.identity.entity.Person;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PersonRepository extends JpaRepository<Person, Long>, JpaSpecificationExecutor<Person> {

    Optional<Person> findByUsername(String username);

    Optional<Person> findByEmail(String email);

    Optional<Person> findByGoogleSubjectId(String googleSubjectId);

    List<Person> findByRolesName(String roleName);
}
