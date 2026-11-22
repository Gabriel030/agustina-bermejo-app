package com.red_escuela.backend.billing.invoice.dto;

import com.red_escuela.backend.identity.entity.Person;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptUploaderTest {

    private Person uploader() {
        Person person = new Person();
        person.setId(3L);
        person.setName("Ana");
        person.setLastname("Paz");
        return person;
    }

    @Test
    void subdirectorUploaderIsReportedWithItsOwnRole() {
        assertThat(ReceiptUploader.of(uploader(), null, List.of("SUBDIRECTOR")).role()).isEqualTo("SUBDIRECTOR");
    }

    @Test
    void directorOutranksSubdirectorAndSubdirectorOutranksAdministrative() {
        assertThat(ReceiptUploader.of(uploader(), null, List.of("SUBDIRECTOR", "DIRECTOR")).role()).isEqualTo("DIRECTOR");
        assertThat(ReceiptUploader.of(uploader(), null, List.of("ADMINISTRATIVO", "SUBDIRECTOR")).role()).isEqualTo("SUBDIRECTOR");
    }
}
