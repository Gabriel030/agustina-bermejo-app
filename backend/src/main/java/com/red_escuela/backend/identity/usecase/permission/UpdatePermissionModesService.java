package com.red_escuela.backend.identity.usecase.permission;

import com.red_escuela.backend.identity.dto.PermissionGridResponse;
import com.red_escuela.backend.identity.dto.UpdatePermissionModesRequest;
import com.red_escuela.backend.identity.specialist.PermissionGridReader;
import com.red_escuela.backend.identity.specialist.PermissionModeEditor;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class UpdatePermissionModesService implements UpdatePermissionModesUseCase {

    private final PermissionModeEditor editor;
    private final PermissionGridReader reader;

    public UpdatePermissionModesService(PermissionModeEditor editor, PermissionGridReader reader) {
        this.editor = editor;
        this.reader = reader;
    }

    @Override
    public PermissionGridResponse update(UpdatePermissionModesRequest request) {
        editor.edit(request);
        return reader.read();
    }
}
