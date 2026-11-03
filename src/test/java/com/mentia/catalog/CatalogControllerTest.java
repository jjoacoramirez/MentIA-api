package com.mentia.catalog;

import static com.mentia.support.Contract.matchesContract;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.mentia.catalog.dto.CategoryResponse;
import com.mentia.support.WebLayerTest;

@WebMvcTest(CatalogController.class)
class CatalogControllerTest extends WebLayerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    CatalogService catalogService;

    @Test
    void devuelveLasCategoriasActivasConElContrato() throws Exception {
        given(catalogService.activeCategories()).willReturn(List.of(
                new CategoryResponse("ATENCION", "Atención", "Foco y respuesta selectiva.", true),
                new CategoryResponse("MEMORIA_VISUAL", "Memoria visual", "Imágenes y posiciones.", true)));

        mockMvc.perform(get("/api/v1/categories").with(authenticatedAs(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].code").value("ATENCION"))
                .andExpect(jsonPath("$[0].mvp").value(true))
                .andExpect(matchesContract());
    }

    @Test
    void requiereAutenticacion() throws Exception {
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
