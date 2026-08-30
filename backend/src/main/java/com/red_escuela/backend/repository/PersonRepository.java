package com.red_escuela.backend.repository;

import com.red_escuela.backend.entity.Person;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PersonRepository extends JpaRepository<Person, Long> {

    Optional<Person> findByUsername(String username);

    Optional<Person> findByEmail(String email);

    List<Person> findByRolesName(String roleName);
}
