package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.evaluation.domain.ClosureExportFormat;
import com.red_escuela.backend.evaluation.entity.ClosureStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClosureExportFileNamerTest {

    private final ClosureExportFileNamer namer = new ClosureExportFileNamer();

    @Test
    void aDraftFileNameIsAsciiWithoutAccentsAndEndsInDraft() {
        String name = namer.nameOf(ClosureSheetFixtures.sheetOf(ClosureStatus.TEACHER_CONFIRMED, true), ClosureExportFormat.XLSX);

        assertThat(name).isEqualTo("cierre-teorias-socioculturales-comision-unica-borrador.xlsx");
    }

    @Test
    void aClosedFileNameCarriesTheReportNumberAndVersion() {
        String name = namer.nameOf(ClosureSheetFixtures.sheetOf(ClosureStatus.CLOSED, true), ClosureExportFormat.PDF);

        assertThat(name).isEqualTo("cierre-teorias-socioculturales-comision-unica-acta-ACTA-2026-0042-v1.pdf");
    }

    @Test
    void anEmptyOrSymbolOnlyNameFallsBackToAGenericSlug() {
        assertThat(namer.slugOf("¿¡ñ?!")).isEqualTo("n");
        assertThat(namer.slugOf("***")).isEqualTo("comision");
        assertThat(namer.slugOf(null)).isEqualTo("comision");
    }
}
