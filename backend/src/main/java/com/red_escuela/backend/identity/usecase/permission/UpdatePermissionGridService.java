package com.red_escuela.backend.identity.usecase.permission;

import com.red_escuela.backend.identity.dto.PermissionGridResponse;
import com.red_escuela.backend.identity.dto.UpdatePermissionGridRequest;
import com.red_escuela.backend.identity.specialist.PermissionGridEditor;
import com.red_escuela.backend.identity.specialist.PermissionGridReader;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class UpdatePermissionGridService implements UpdatePermissionGridUseCase {

    private final PermissionGridEditor editor;
    private final PermissionGridReader reader;

    public UpdatePermissionGridService(PermissionGridEditor editor, PermissionGridReader reader) {
        this.editor = editor;
        this.reader = reader;
    }

    @Override
    public PermissionGridResponse update(UpdatePermissionGridRequest request) {
        editor.edit(request);
        return reader.read();
    }
}
