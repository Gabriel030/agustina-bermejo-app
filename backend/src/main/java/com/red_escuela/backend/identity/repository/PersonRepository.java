package com.red_escuela.backend.identity.repository;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.domain.DocumentType;
import com.red_escuela.backend.shared.domain.EmailAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PersonRepository extends JpaRepository<Person, Long>, JpaSpecificationExecutor<Person> {

    @Modifying(flushAutomatically = true)
    @Query(value = """
            update person set reset_lockout_notified_at = :now
            where id = :personId and (reset_lockout_notified_at is null or reset_lockout_notified_at <= :notifiedBefore)
            """, nativeQuery = true)
    int claimResetLockoutNotice(@Param("personId") Long personId, @Param("now") LocalDateTime now,
                                @Param("notifiedBefore") LocalDateTime notifiedBefore);

    @Modifying(flushAutomatically = true)
    @Query("update Person p set p.tokenVersion = p.tokenVersion + 1 where p.id = :personId")
    int incrementTokenVersion(@Param("personId") Long personId);

    @Query("select p.tokenVersion from Person p where p.id = :personId")
    Integer findTokenVersionById(@Param("personId") Long personId);

    Optional<Person> findByUsername(String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Person p where p.id = :id")
    Optional<Person> findByIdForUpdate(@Param("id") Long id);

    @Query("select count(p) from Person p where p.id in :ids and (p.grantSystemAccess = true or p.password is not null)")
    long countWithSystemAccessByIdIn(@Param("ids") Collection<Long> ids);

    @Query("""
            select distinct p from Person p join p.roles r
            where p.id in :ids and r.name = 'ALUMNO' and p.status = com.red_escuela.backend.shared.domain.StatusType.ACTIVE and p.password is null
              and (p.grantSystemAccess is null or p.grantSystemAccess = false)
            order by p.id
            """)
    List<Person> findStudentsPendingActivationByIdIn(@Param("ids") Collection<Long> ids);

    @Query(value = "select p.id as id, p.birth_date as birthDate from person p where p.id = any(:ids) and p.birth_date is not null and p.birth_date > :earliestBirthDateOfAdults", nativeQuery = true)
    List<PersonBirthDateRow> findBirthDatesNewerThan(@Param("ids") Long[] ids, @Param("earliestBirthDateOfAdults") java.time.LocalDate earliestBirthDateOfAdults);

    @Query("select p.address.id from Person p where p.id in :ids and p.address is not null")
    List<Long> findAddressIdsByIdIn(@Param("ids") Collection<Long> ids);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from person_role where person_id in :ids", nativeQuery = true)
    int deleteRolesByPersonIdIn(@Param("ids") Collection<Long> ids);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Person p where p.id in :ids")
    int deleteByIdIn(@Param("ids") Collection<Long> ids);

    @Query("select new com.red_escuela.backend.identity.repository.AccountAccessState(p.status, p.tokenVersion) from Person p where p.id = :personId")
    Optional<AccountAccessState> findAccessStateById(@Param("personId") Long personId);

    boolean existsByRoles_Name(String roleName);

    @Query("select distinct p from Person p left join fetch p.roles where p.id in :personIds")
    List<Person> findAllWithRolesByIdIn(@Param("personIds") Collection<Long> personIds);

    default List<Person> findAllWithRoles(
            org.springframework.data.jpa.domain.Specification<Person> spec, org.springframework.data.domain.Sort sort) {
        List<Person> people = findAll(spec, sort);
        if (!people.isEmpty()) {
            findAllWithRolesByIdIn(people.stream().map(Person::getId).toList());
        }
        return people;
    }

    Optional<Person> findByEmail(EmailAddress email);

    Optional<Person> findByDocumentTypeAndDni(DocumentType documentType, String dni);

    Optional<Person> findByGoogleSubjectId(String googleSubjectId);

    List<Person> findByRolesName(String roleName);
}
