package com.red_escuela.backend.dataimport.specialist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.red_escuela.backend.dataimport.exception.ImportConfirmationInvalidException;
import org.junit.jupiter.api.Test;

class ImportReportWriterTest {

    @Test
    void cellsStartingWithFormulaCharactersAreNeutralized() {
        assertThat(ImportReportWriter.cell("=SUM(A1)")).isEqualTo("'=SUM(A1)");
        assertThat(ImportReportWriter.cell("+54")).isEqualTo("'+54");
        assertThat(ImportReportWriter.cell("@cmd")).isEqualTo("'@cmd");
    }

    @Test
    void cellsWithSeparatorsQuotesOrLineBreaksAreQuoted() {
        assertThat(ImportReportWriter.cell("a,b")).isEqualTo("\"a,b\"");
        assertThat(ImportReportWriter.cell("di\"jo")).isEqualTo("\"di\"\"jo\"");
        assertThat(ImportReportWriter.cell("uno\ndos")).isEqualTo("\"uno\ndos\"");
        assertThat(ImportReportWriter.cell("plano")).isEqualTo("plano");
        assertThat(ImportReportWriter.cell("")).isEmpty();
    }

    @Test
    void confirmationAcceptsTheExpectedWordIgnoringCaseAndSurroundingSpaces() {
        ImportConfirmation.require("  cerrar ", ImportConfirmation.SEAL_WORD);
        ImportConfirmation.require("DESHACER", ImportConfirmation.ROLLBACK_WORD);
    }

    @Test
    void confirmationRejectsOtherWordsAndNull() {
        assertThatThrownBy(() -> ImportConfirmation.require("DESHACER", ImportConfirmation.SEAL_WORD))
                .isInstanceOf(ImportConfirmationInvalidException.class);
        assertThatThrownBy(() -> ImportConfirmation.require("CERRARR", ImportConfirmation.SEAL_WORD))
                .isInstanceOf(ImportConfirmationInvalidException.class);
        assertThatThrownBy(() -> ImportConfirmation.require(null, ImportConfirmation.SEAL_WORD))
                .isInstanceOf(ImportConfirmationInvalidException.class);
    }
}
