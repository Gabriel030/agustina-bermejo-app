package com.red_escuela.backend.evaluation.domain;

import com.red_escuela.backend.evaluation.exception.InvalidClosureExportFormatException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClosureExportFormatTest {

    @Test
    void theFormatIsParsedIgnoringCaseAndSurroundingSpaces() {
        assertThat(ClosureExportFormat.fromParameter("XLSX")).isEqualTo(ClosureExportFormat.XLSX);
        assertThat(ClosureExportFormat.fromParameter(" pdf ")).isEqualTo(ClosureExportFormat.PDF);
    }

    @Test
    void anUnknownOrMissingFormatIsRejectedWithAnErrorThatCarriesTheValue() {
        assertThatThrownBy(() -> ClosureExportFormat.fromParameter("csv")).isInstanceOf(InvalidClosureExportFormatException.class);
        assertThatThrownBy(() -> ClosureExportFormat.fromParameter(null)).isInstanceOf(InvalidClosureExportFormatException.class);
    }

    @Test
    void theContentTypesMatchTheFormats() {
        assertThat(ClosureExportFormat.PDF.contentType()).isEqualTo("application/pdf");
        assertThat(ClosureExportFormat.XLSX.contentType()).contains("spreadsheetml");
    }
}
