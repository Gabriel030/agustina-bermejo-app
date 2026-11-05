package com.red_escuela.backend.platform.usecase;

import com.red_escuela.backend.platform.domain.FeatureChangeEntry;
import com.red_escuela.backend.platform.domain.FeatureChangeFilter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ListFeatureChangesUseCase {
    Page<FeatureChangeEntry> list(FeatureChangeFilter filter, Pageable pageable);
}
