package com.red_escuela.backend.billing.configuration.repository;

import com.red_escuela.backend.billing.configuration.domain.ConfigurationScope;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersion;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ConfigurationVersionRepository extends JpaRepository<ConfigurationVersion, Long> {

    @Query(value = """
            select * from configuration_version
            where scope = :scope and topic_key = :topicKey and status in ('SCHEDULED', 'EFFECTIVE', 'SUPERSEDED') and effective_from <= :date
            order by effective_from desc, decided_at desc, id desc
            limit 1
            """, nativeQuery = true)
    Optional<ConfigurationVersion> findEffectiveVersion(@Param("scope") String scope, @Param("topicKey") String topicKey, @Param("date") LocalDate date);

    @Query(value = """
            select * from configuration_version
            where scope = :scope and topic_key = :topicKey and status in ('SCHEDULED', 'EFFECTIVE', 'SUPERSEDED')
            order by effective_from asc, decided_at asc, id asc
            limit 1
            """, nativeQuery = true)
    Optional<ConfigurationVersion> findFirstVersion(@Param("scope") String scope, @Param("topicKey") String topicKey);

    boolean existsByScopeAndTopicKeyAndStatus(ConfigurationScope scope, String topicKey, ConfigurationVersionStatus status);

    Optional<ConfigurationVersion> findFirstByScopeAndTopicKeyAndStatus(ConfigurationScope scope, String topicKey, ConfigurationVersionStatus status);

    Optional<ConfigurationVersion> findFirstByAuthorizationRequestId(Long authorizationRequestId);

    List<ConfigurationVersion> findByScopeAndTopicKeyOrderByEffectiveFromDescIdDesc(ConfigurationScope scope, String topicKey);

    List<ConfigurationVersion> findByStatusAndEffectiveFromLessThanEqual(ConfigurationVersionStatus status, LocalDate date);

    List<ConfigurationVersion> findByScopeAndTopicKeyAndStatus(ConfigurationScope scope, String topicKey, ConfigurationVersionStatus status);
}
