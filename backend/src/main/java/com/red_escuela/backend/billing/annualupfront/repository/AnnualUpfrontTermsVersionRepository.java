package com.red_escuela.backend.billing.annualupfront.repository;

import com.red_escuela.backend.billing.annualupfront.domain.AnnualUpfrontTermsVersion;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AnnualUpfrontTermsVersionRepository extends JpaRepository<AnnualUpfrontTermsVersion, Long> {

    Optional<AnnualUpfrontTermsVersion> findFirstByOrderByVersionNumberDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from AnnualUpfrontTermsVersion t order by t.versionNumber desc limit 1")
    Optional<AnnualUpfrontTermsVersion> findLatestForUpdate();

    Optional<AnnualUpfrontTermsVersion> findByVersionNumber(Integer versionNumber);

    List<AnnualUpfrontTermsVersion> findAllByOrderByVersionNumberDesc();
}
