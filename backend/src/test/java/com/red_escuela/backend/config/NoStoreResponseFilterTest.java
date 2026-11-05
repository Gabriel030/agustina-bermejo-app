package com.red_escuela.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class NoStoreResponseFilterTest {
    private final NoStoreResponseFilter filter = new NoStoreResponseFilter();

    private MockHttpServletResponse call(String uri) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void authMeAndMeSubpathsAreMarkedNoStore() throws Exception {
        for (String uri : new String[] {"/auth/login", "/me", "/me/documents"}) {
            assertThat(call(uri).getHeader("Cache-Control")).as(uri).isEqualTo("no-store");
        }
    }

    @Test
    void pathsThatOnlyShareThePrefixTextAreNotMarked() throws Exception {
        for (String uri : new String[] {"/institutions", "/media", "/authors"}) {
            assertThat(call(uri).getHeader("Cache-Control")).as(uri).isNull();
        }
    }
}
