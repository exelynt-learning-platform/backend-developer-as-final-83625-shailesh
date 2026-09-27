package com.example.booking;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ResourceSecurityTest extends AbstractIntegrationTest {

    private static final String NEW_RESOURCE = "{\"name\":\"Hall\",\"description\":\"Big hall\",\"type\":\"room\"}";

    // ---------- USER is read-only ----------

    @Test
    void userCanListAndReadResources() throws Exception {
        String token = user1Token();
        mockMvc.perform(get("/resources").header(AUTH, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
        mockMvc.perform(get("/resources/" + resourceId(0)).header(AUTH, bearer(token)))
                .andExpect(status().isOk());
    }

    @Test
    void userCannotCreateResource() throws Exception {
        mockMvc.perform(post("/resources").header(AUTH, bearer(user1Token()))
                        .contentType(MediaType.APPLICATION_JSON).content(NEW_RESOURCE))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void userCannotUpdateResource() throws Exception {
        mockMvc.perform(put("/resources/" + resourceId(0)).header(AUTH, bearer(user1Token()))
                        .contentType(MediaType.APPLICATION_JSON).content(NEW_RESOURCE))
                .andExpect(status().isForbidden());
    }

    @Test
    void userCannotDeleteResource() throws Exception {
        mockMvc.perform(delete("/resources/" + resourceId(0)).header(AUTH, bearer(user1Token())))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousCannotReadResources() throws Exception {
        mockMvc.perform(get("/resources")).andExpect(status().isUnauthorized());
    }

    // ---------- ADMIN has full CRUD ----------

    @Test
    void adminCanCreateResourceAndTypeIsNormalised() throws Exception {
        mockMvc.perform(post("/resources").header(AUTH, bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON).content(NEW_RESOURCE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.type").value("ROOM"));
    }

    @Test
    void adminCanUpdateResource() throws Exception {
        mockMvc.perform(put("/resources/" + resourceId(0)).header(AUTH, bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\",\"description\":\"d\",\"type\":\"ROOM\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"));
    }

    @Test
    void adminCanDeleteResourceThenItIsGone() throws Exception {
        String token = adminToken();
        Long id = resourceId(2);
        mockMvc.perform(delete("/resources/" + id).header(AUTH, bearer(token)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/resources/" + id).header(AUTH, bearer(token)))
                .andExpect(status().isNotFound());
    }

    // ---------- validation and missing data ----------

    @Test
    void invalidResourceBodyReturns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/resources").header(AUTH, bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\",\"type\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.type").exists());
    }

    @Test
    void unknownResourceReturns404() throws Exception {
        mockMvc.perform(get("/resources/999999").header(AUTH, bearer(user1Token())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}