package com.red_escuela.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class MultipartRequestSizeFilterTest {

    private static final long MB = 1024L * 1024;

    private final MultipartRequestSizeFilter filter = new MultipartRequestSizeFilter();

    private MockHttpServletResponse send(String path, String contentType, long contentLength, MockFilterChain chain) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRequestURI(path);
        request.setContentType(contentType);
        request.setContent(new byte[(int) contentLength]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    void theProgramApplicationEndpointAcceptsAnEightMegabyteMultipartRequest() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = send("/me/program-enrollment-applications/with-documents", "multipart/form-data; boundary=x", 8 * MB, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void theProgramApplicationEndpointRejectsMoreThanTenMegabytes() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = send("/me/program-enrollment-applications/with-documents", "multipart/form-data; boundary=x", 11 * MB, chain);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("MAX_UPLOAD_SIZE_EXCEEDED");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void anyOtherMultipartEndpointRejectsMoreThanSixMegabytes() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = send("/me/course-enrollments", "multipart/form-data; boundary=x", 7 * MB, chain);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("MAX_UPLOAD_SIZE_EXCEEDED");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void anyOtherMultipartEndpointAcceptsFiveMegabytes() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = send("/me/course-enrollments", "multipart/form-data; boundary=x", 5 * MB, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void nonMultipartRequestsAreNotLimitedByThisFilter() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = send("/me/course-enrollments", "application/json", 7 * MB, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }
}
