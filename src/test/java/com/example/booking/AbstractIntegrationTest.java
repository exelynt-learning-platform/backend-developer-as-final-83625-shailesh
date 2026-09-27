package com.example.booking;

import com.example.booking.repository.ResourceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional // every test rolls back, so tests stay independent
public abstract class AbstractIntegrationTest {

    protected static final String AUTH = "Authorization";

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected ResourceRepository resourceRepository;

    // ---------- auth helpers ----------

    protected String login(String username, String password) throws Exception {
        String json = String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, password);
        String body = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    protected String adminToken() throws Exception { return login("admin", "admin123"); }
    protected String user1Token() throws Exception { return login("user1", "user123"); }
    protected String user2Token() throws Exception { return login("user2", "user123"); }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }

    // ---------- data helpers ----------

    /** Seeded resources (DataSeeder creates 3). */
    protected Long resourceId(int index) {
        return resourceRepository.findAll().get(index).getId();
    }

    /** A future time slot: 10 days from now at the given hour (0-23). */
    protected static LocalDateTime slot(int hour) {
        return LocalDateTime.now().plusDays(10).truncatedTo(ChronoUnit.DAYS).withHour(hour);
    }

    protected static String reservationJson(Long resourceId, LocalDateTime start,
                                            LocalDateTime end, String price) {
        return String.format(
                "{\"resourceId\":%d,\"startTime\":\"%s\",\"endTime\":\"%s\",\"price\":%s}",
                resourceId, start, end, price);
    }

    protected static String updateJson(Long resourceId, LocalDateTime start, LocalDateTime end,
                                       String price, String status) {
        return String.format(
                "{\"resourceId\":%d,\"startTime\":\"%s\",\"endTime\":\"%s\",\"price\":%s,\"status\":\"%s\"}",
                resourceId, start, end, price, status);
    }

    /** Creates a 1-hour reservation starting at startHour and returns its id. */
    protected long createReservation(String token, Long resourceId, int startHour, String price)
            throws Exception {
        String json = reservationJson(resourceId, slot(startHour), slot(startHour + 1), price);
        String body = mockMvc.perform(post("/reservations")
                        .header(AUTH, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }
}