package com.red_escuela.backend.platform.usecase.settings;

import com.red_escuela.backend.platform.domain.InstituteSettingChangeEntry;
import com.red_escuela.backend.platform.specialist.InstituteSettingChangeEntryReader;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@UseCase
public class ListInstituteSettingChangesService implements ListInstituteSettingChangesUseCase {

    private final InstituteSettingChangeEntryReader changeEntryReader;

    public ListInstituteSettingChangesService(InstituteSettingChangeEntryReader changeEntryReader) {
        this.changeEntryReader = changeEntryReader;
    }

    @Override
    public Page<InstituteSettingChangeEntry> list(Pageable pageable) {
        return changeEntryReader.readPage(pageable);
    }
}
