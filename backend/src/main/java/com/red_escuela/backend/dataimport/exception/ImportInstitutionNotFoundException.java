package com.red_escuela.backend.dataimport.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;
import com.red_escuela.backend.shared.exception.ReportsErrorCode;

public class ImportInstitutionNotFoundException extends NotFoundException implements ReportsErrorCode {
    public ImportInstitutionNotFoundException() {
        super("No hay una institución configurada para importar");
    }
}
