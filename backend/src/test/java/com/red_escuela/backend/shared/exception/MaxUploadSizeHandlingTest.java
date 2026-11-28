package com.red_escuela.backend.shared.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.assertj.core.api.Assertions.assertThat;

class MaxUploadSizeHandlingTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void oversizedSignatoryUploadIsMappedToSignatureInvalid() {
        ResponseEntity<ErrorCodeResponse> response = handler.handleMaxUploadSize(
                new MaxUploadSizeExceededException(5L * 1024 * 1024), new MockHttpServletRequest("PUT", "/certificate-signatories/7/signature"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().code()).isEqualTo("SIGNATORY_SIGNATURE_INVALID");
    }

    @Test
    void oversizedUploadOnAnotherEndpointKeepsBadRequest() {
        ResponseEntity<ErrorCodeResponse> response = handler.handleMaxUploadSize(
                new MaxUploadSizeExceededException(5L * 1024 * 1024), new MockHttpServletRequest("POST", "/certificate-templates/PROGRAM/images"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void oversizedNonPutOnTheSignatureUrlKeepsBadRequest() {
        ResponseEntity<ErrorCodeResponse> response = handler.handleMaxUploadSize(
                new MaxUploadSizeExceededException(5L * 1024 * 1024), new MockHttpServletRequest("POST", "/certificate-signatories/7/signature"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void oversizedDataImportUploadIsMappedToImportFileTooLarge() {
        ResponseEntity<ErrorCodeResponse> response = handler.handleMaxUploadSize(
                new MaxUploadSizeExceededException(5L * 1024 * 1024), new MockHttpServletRequest("POST", "/data-imports/12/files/PERSONS"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("IMPORT_FILE_TOO_LARGE");
    }
}
