package com.red_escuela.backend.security;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UnpaginatedDebtorsEndpointRetiredTest extends AuthorizationMatrixSupport {

    @Test
    void unpaginatedDebtorsListingNoLongerExistsForTheDirector() throws Exception {
        mockMvc.perform(get("/financial-dashboard/debtors")
                        .header("Authorization", "Bearer " + fixtures.tokenFor(MatrixRole.DIRECTOR)))
                .andExpect(status().isNotFound());
    }

    @Test
    void paginatedDebtorsListingStillAnswersTheDirector() throws Exception {
        mockMvc.perform(get("/financial-dashboard/debtors/page")
                        .header("Authorization", "Bearer " + fixtures.tokenFor(MatrixRole.DIRECTOR)))
                .andExpect(status().isOk());
    }
}
