package com.mentia.catalog;

import static com.mentia.support.Contract.matchesContract;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import com.mentia.security.JwtService;
import com.mentia.support.IntegrationTest;
import com.mentia.security.Role;

class CategoriesIntegrationTest extends IntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtService jwtService;

    @Test
    void listaSoloLasCategoriasActivasPrimeroLasDelMvp() throws Exception {
        String token = jwtService.issue(UUID.randomUUID(), 0, Role.USER).token();

        mockMvc.perform(get("/api/v1/categories").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code").value(contains("ATENCION", "MEMORIA_TRABAJO", "MEMORIA_VISUAL")))
                .andExpect(matchesContract());
    }
}
