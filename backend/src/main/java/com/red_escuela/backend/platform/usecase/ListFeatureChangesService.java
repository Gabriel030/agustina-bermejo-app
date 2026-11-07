package com.red_escuela.backend.platform.usecase;

import com.red_escuela.backend.platform.domain.FeatureChangeEntry;
import com.red_escuela.backend.platform.domain.FeatureChangeFilter;
import com.red_escuela.backend.platform.specialist.FeatureChangeEntryReader;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@UseCase
public class ListFeatureChangesService implements ListFeatureChangesUseCase {

    private final FeatureChangeEntryReader changeEntryReader;

    public ListFeatureChangesService(FeatureChangeEntryReader changeEntryReader) {
        this.changeEntryReader = changeEntryReader;
    }

    @Override
    public Page<FeatureChangeEntry> list(FeatureChangeFilter filter, Pageable pageable) {
        return changeEntryReader.readPage(filter, pageable);
    }
}
