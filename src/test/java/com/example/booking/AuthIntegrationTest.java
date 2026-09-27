package com.example.booking;

import com.example.booking.entity.User;
import com.example.booking.repository.UserRepository;
import com.example.booking.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegrationTest extends AbstractIntegrationTest {

    @Autowired UserRepository userRepository;
    @Value("${app.jwt.secret}") String jwtSecret;

    private String loginJson(String user, String pass) {
        return String.format("{\"username\":\"%s\",\"password\":\"%s\"}", user, pass);
    }

    @Test
    void validLoginReturnsTokenAndRole() throws Exception {
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("admin", "admin123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void wrongPasswordReturns401() throws Exception {
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("admin", "wrong")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void unknownUserGetsSameMessageAsWrongPassword() throws Exception {
        // must not reveal which usernames exist
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("ghost", "whatever")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void blankLoginBodyReturns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.username").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void passwordsAreStoredAsBCryptHashes() {
        User admin = userRepository.findByUsername("admin").orElseThrow();
        assertNotEquals("admin123", admin.getPassword());
        assertTrue(admin.getPassword().startsWith("$2"), "expected a BCrypt hash");
    }

    @Test
    void protectedEndpointWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/resources"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void garbageTokenReturns401() throws Exception {
        mockMvc.perform(get("/resources").header(AUTH, "Bearer not.a.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongAuthSchemeReturns401() throws Exception {
        mockMvc.perform(get("/resources").header(AUTH, "Basic YWRtaW46YWRtaW4xMjM="))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenReturns401() throws Exception {
        String expired = new JwtService(jwtSecret, -1_000).generateToken("admin", "ADMIN");
        mockMvc.perform(get("/resources").header(AUTH, bearer(expired)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenForDeletedUserReturns401() throws Exception {
        String token = new JwtService(jwtSecret, 60_000).generateToken("deleted-user", "ADMIN");
        mockMvc.perform(get("/resources").header(AUTH, bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validTokenGrantsAccess() throws Exception {
        mockMvc.perform(get("/resources").header(AUTH, bearer(user1Token())))
                .andExpect(status().isOk());
    }
}