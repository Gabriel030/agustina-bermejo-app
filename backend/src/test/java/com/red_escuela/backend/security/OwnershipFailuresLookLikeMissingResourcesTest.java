package com.red_escuela.backend.security;

import com.red_escuela.backend.academicstructure.exception.NotOwnSectionException;
import com.red_escuela.backend.academicstructure.exception.SectionNotFoundException;
import com.red_escuela.backend.announcement.exception.NotSectionInstructorException;
import com.red_escuela.backend.billing.exception.NotOwnEnrollmentException;
import com.red_escuela.backend.enrollment.exception.CourseEnrollmentNotFoundException;
import com.red_escuela.backend.shared.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class OwnershipFailuresLookLikeMissingResourcesTest {

    @RestController
    static class ThrowingController {
        @GetMapping("/own-enrollment")
        void ownEnrollment() {
            throw new NotOwnEnrollmentException(1L, 2L);
        }

        @GetMapping("/missing-enrollment")
        void missingEnrollment() {
            throw new CourseEnrollmentNotFoundException(2L);
        }

        @GetMapping("/own-section")
        void ownSection() {
            throw new NotOwnSectionException(1L, 2L);
        }

        @GetMapping("/section-instructor")
        void sectionInstructor() {
            throw new NotSectionInstructorException(2L);
        }

        @GetMapping("/missing-section")
        void missingSection() {
            throw new SectionNotFoundException(2L);
        }
    }

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    private MvcResult call(String path) throws Exception {
        return mockMvc.perform(get(path)).andReturn();
    }

    @Test
    void notOwnEnrollmentIsIndistinguishableFromMissingEnrollment() throws Exception {
        MvcResult foreign = call("/own-enrollment");
        MvcResult missing = call("/missing-enrollment");

        assertThat(foreign.getResponse().getStatus()).isEqualTo(404);
        assertThat(foreign.getResponse().getStatus()).isEqualTo(missing.getResponse().getStatus());
        assertThat(foreign.getResponse().getContentAsString()).isEqualTo(missing.getResponse().getContentAsString());
    }

    @Test
    void notOwnSectionIsIndistinguishableFromMissingSection() throws Exception {
        MvcResult foreign = call("/own-section");
        MvcResult missing = call("/missing-section");

        assertThat(foreign.getResponse().getStatus()).isEqualTo(404);
        assertThat(foreign.getResponse().getStatus()).isEqualTo(missing.getResponse().getStatus());
        assertThat(foreign.getResponse().getContentAsString()).isEqualTo(missing.getResponse().getContentAsString());
    }

    @Test
    void notSectionInstructorIsIndistinguishableFromMissingSection() throws Exception {
        MvcResult foreign = call("/section-instructor");
        MvcResult missing = call("/missing-section");

        assertThat(foreign.getResponse().getStatus()).isEqualTo(404);
        assertThat(foreign.getResponse().getContentAsString()).isEqualTo(missing.getResponse().getContentAsString());
    }
}
