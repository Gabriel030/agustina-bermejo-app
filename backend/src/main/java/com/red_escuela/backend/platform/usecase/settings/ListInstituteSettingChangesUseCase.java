package com.red_escuela.backend.platform.usecase.settings;

import com.red_escuela.backend.platform.domain.InstituteSettingChangeEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ListInstituteSettingChangesUseCase {
    Page<InstituteSettingChangeEntry> list(Pageable pageable);
}
